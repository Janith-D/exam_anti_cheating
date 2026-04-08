package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.anti_cheating_backend.dto.identity.UnifiedScorePacketDTO;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.IdentityAlert;
import com.example.anti_cheating_backend.entity.IdentityAttempt;
import com.example.anti_cheating_backend.entity.IdentitySession;
import com.example.anti_cheating_backend.repo.IdentityAlertRepo;
import com.example.anti_cheating_backend.repo.IdentityAttemptRepo;
import com.example.anti_cheating_backend.repo.IdentitySessionRepo;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IdentityVerificationService {

    private static final Logger LOGGER = Logger.getLogger(IdentityVerificationService.class.getName());

    @Autowired
    private IdentitySessionRepo identitySessionRepo;

    @Autowired
    private IdentityAttemptRepo identityAttemptRepo;

    @Autowired
    private IdentityAlertRepo identityAlertRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceUrl;

    @Value("${ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> startVerificationSession(Long studentId, Long examSessionId, Integer ttlSeconds) {
        if (studentId == null) {
            throw new RuntimeException("studentId is required");
        }

        int ttl = ttlSeconds != null && ttlSeconds > 0 ? ttlSeconds : 300;

        IdentitySession session = new IdentitySession();
        session.setSessionId(UUID.randomUUID().toString());
        session.setStudentId(studentId);
        session.setExamSessionId(examSessionId);
        session.setState(Enums.IdentitySessionState.CREATED);
        session.setStartedAt(LocalDateTime.now());
        session.setExpiresAt(LocalDateTime.now().plusSeconds(ttl));

        session = identitySessionRepo.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("studentId", session.getStudentId());
        response.put("examSessionId", session.getExamSessionId());
        response.put("state", session.getState());
        response.put("startedAt", session.getStartedAt());
        response.put("expiresAt", session.getExpiresAt());
        return response;
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> submitVerification(String sessionId, Long studentId, String image) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId is required");
        }
        if (studentId == null) {
            throw new RuntimeException("studentId is required");
        }
        if (image == null || image.isBlank()) {
            throw new RuntimeException("image is required");
        }

        IdentitySession session = identitySessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Identity session not found: " + sessionId));

        if (!studentId.equals(session.getStudentId())) {
            throw new RuntimeException("studentId does not match session owner");
        }

        if (LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setState(Enums.IdentitySessionState.EXPIRED);
            identitySessionRepo.save(session);
            throw new RuntimeException("Identity session expired");
        }

        session.setState(Enums.IdentitySessionState.SUBMITTED);
        identitySessionRepo.save(session);

        Map<String, Object> verifyResponse = callMlVerify(studentId, image);

        Map<String, Object> scorePacketMap;
        Object packetObj = verifyResponse.get("scorePacket");
        if (packetObj instanceof Map) {
            scorePacketMap = (Map<String, Object>) packetObj;
        } else {
            scorePacketMap = buildFallbackScorePacket(sessionId, studentId, verifyResponse);
        }

        UnifiedScorePacketDTO packet = objectMapper.convertValue(scorePacketMap, UnifiedScorePacketDTO.class);
        List<String> mlReasonCodes = extractMlReasonCodes(verifyResponse);

        boolean fullModalitiesPresent = isFullMultimodalPacket(packet);
        double fusedScore = fullModalitiesPresent ? computeFullFusionScore(packet) : computeFaceOnlyFusionScore(packet);

        List<String> reasonCodes = new ArrayList<>();
        Enums.IdentityDecision decision = applyDecisionPolicy(
            packet,
            fusedScore,
            fullModalitiesPresent,
            mlReasonCodes,
            reasonCodes
        );

        Map<String, Object> rawAttemptPayload = new HashMap<>();
        rawAttemptPayload.put("scorePacket", scorePacketMap);
        rawAttemptPayload.put("contractVersion", verifyResponse.getOrDefault("contractVersion", "unified-score-packet/v1"));
        rawAttemptPayload.put("mlReasonCodes", mlReasonCodes);

        IdentityAttempt attempt = new IdentityAttempt();
        attempt.setAttemptId(UUID.randomUUID().toString());
        attempt.setSession(session);
        attempt.setRawScoresJson(toJson(rawAttemptPayload));
        attempt.setFusedScore(fusedScore);
        attempt.setDecision(decision);
        attempt.setReasonCodes(toJson(reasonCodes));
        attempt.setLatencyMs(packet.getLatencyMs());
        attempt = identityAttemptRepo.save(attempt);

        if (decision == Enums.IdentityDecision.BLOCK) {
            createIdentityAlert(session, reasonCodes, scorePacketMap, Enums.AlertSeverity.HIGH, "POLICY_BLOCK");
        }

        session.setState(Enums.IdentitySessionState.DECIDED);
        identitySessionRepo.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("attemptId", attempt.getAttemptId());
        response.put("fusedScore", fusedScore);
        response.put("decision", decision);
        response.put("reasonCodes", reasonCodes);
        response.put("mlReasonCodes", mlReasonCodes);
        response.put("contractVersion", verifyResponse.getOrDefault("contractVersion", "unified-score-packet/v1"));
        response.put("scorePacket", scorePacketMap);
        return response;
    }

    public Map<String, Object> requestStepUp(String sessionId, String trigger) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId is required");
        }

        IdentitySession session = identitySessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Identity session not found: " + sessionId));

        String challengeToken = UUID.randomUUID().toString();
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(2);

        createIdentityAlert(
                session,
                List.of("STEP_UP_REQUESTED", trigger != null ? trigger : "MANUAL_TRIGGER"),
                Map.of("challengeToken", challengeToken),
                Enums.AlertSeverity.MEDIUM,
                "STEP_UP"
        );

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("challengeToken", challengeToken);
        response.put("challengeType", "VOICE_AND_LIVENESS");
        response.put("expiresAt", expiresAt);
        response.put("status", "STEP_UP_REQUIRED");
        return response;
    }

    public Map<String, Object> monitorPing(String sessionId, Map<String, Object> signals) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId is required");
        }

        IdentitySession session = identitySessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Identity session not found: " + sessionId));

        IdentityAttempt latest = identityAttemptRepo.findTopBySessionSessionIdOrderByCreatedAtDesc(sessionId).orElse(null);
        double currentScore = latest != null ? val(latest.getFusedScore()) : 0.0;
        boolean highRiskSignal = hasHighRiskSignal(signals);

        String action;
        if (highRiskSignal || currentScore < 0.65) {
            action = "RECHECK_REQUIRED";
        } else if (currentScore < 0.80) {
            action = "STEP_UP_RECOMMENDED";
        } else {
            action = "CONTINUE";
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("action", action);
        response.put("currentDecision", latest != null ? latest.getDecision() : "PENDING");
        response.put("currentFusedScore", currentScore);
        response.put("receivedSignals", signals == null ? Map.of() : signals);
        response.put("timestamp", LocalDateTime.now());
        return response;
    }

    public Map<String, Object> monitorRecheck(String sessionId, Long studentId, String image) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId is required");
        }

        if (image == null || image.isBlank()) {
            return getVerificationResult(sessionId);
        }

        return submitVerification(sessionId, studentId, image);
    }

    public Map<String, Object> proctorOverride(String sessionId, String decision, String reason, String proctorId) {
        if (sessionId == null || sessionId.isBlank()) {
            throw new RuntimeException("sessionId is required");
        }
        if (decision == null || decision.isBlank()) {
            throw new RuntimeException("decision is required");
        }

        IdentitySession session = identitySessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Identity session not found: " + sessionId));

        Enums.IdentityDecision overrideDecision;
        try {
            overrideDecision = Enums.IdentityDecision.valueOf(decision.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new RuntimeException("Invalid decision. Use ALLOW, STEP_UP, or BLOCK");
        }

        IdentityAttempt attempt = new IdentityAttempt();
        attempt.setAttemptId(UUID.randomUUID().toString());
        attempt.setSession(session);
        attempt.setRawScoresJson(toJson(Map.of(
                "override", true,
                "proctorId", proctorId,
                "reason", reason
        )));
        attempt.setFusedScore(overrideDecision == Enums.IdentityDecision.ALLOW ? 1.0 : 0.0);
        attempt.setDecision(overrideDecision);
        attempt.setReasonCodes(toJson(List.of("PROCTOR_OVERRIDE", reason == null ? "NO_REASON" : reason)));
        attempt.setLatencyMs(0);
        identityAttemptRepo.save(attempt);

        session.setState(Enums.IdentitySessionState.DECIDED);
        identitySessionRepo.save(session);

        if (overrideDecision == Enums.IdentityDecision.BLOCK) {
            createIdentityAlert(
                    session,
                    List.of("PROCTOR_OVERRIDE_BLOCK", reason == null ? "NO_REASON" : reason),
                    Map.of("proctorId", proctorId),
                    Enums.AlertSeverity.CRITICAL,
                    "PROCTOR_OVERRIDE"
            );
        }

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", sessionId);
        response.put("decision", overrideDecision);
        response.put("reason", reason);
        response.put("proctorId", proctorId);
        response.put("status", "OVERRIDDEN");
        return response;
    }

    public Map<String, Object> getVerificationResult(String sessionId) {
        IdentitySession session = identitySessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Identity session not found: " + sessionId));

        IdentityAttempt latest = identityAttemptRepo.findTopBySessionSessionIdOrderByCreatedAtDesc(sessionId).orElse(null);

        Map<String, Object> response = new HashMap<>();
        response.put("sessionId", session.getSessionId());
        response.put("studentId", session.getStudentId());
        response.put("examSessionId", session.getExamSessionId());
        response.put("state", session.getState());
        response.put("startedAt", session.getStartedAt());
        response.put("expiresAt", session.getExpiresAt());

        if (latest != null) {
            response.put("attemptId", latest.getAttemptId());
            response.put("fusedScore", latest.getFusedScore());
            response.put("decision", latest.getDecision());
            response.put("reasonCodes", fromJsonArray(latest.getReasonCodes()));
            response.put("createdAt", latest.getCreatedAt());
        } else {
            response.put("decision", "PENDING");
            response.put("reasonCodes", List.of("NO_ATTEMPT_YET"));
        }

        return response;
    }

    private Map<String, Object> callMlVerify(Long studentId, String image) {
        Map<String, Object> payload = Map.of(
                "studentId", studentId,
                "image", image
        );

        if (!mlServiceEnabled) {
            LOGGER.info("ML service disabled; returning mock verify response for Step 2");
            return mockVerifyResponse(studentId);
        }

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    mlServiceUrl + "/verify",
                    payload,
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("ML verify request failed with status: " + response.getStatusCode());
            }
            if (!(response.getBody() instanceof Map<?, ?> bodyMap)) {
                throw new RuntimeException("ML verify response is not a JSON object");
            }
            return objectMapper.convertValue(bodyMap, new TypeReference<Map<String, Object>>() {});
        } catch (RestClientException e) {
            throw new RuntimeException("ML service connection failed: " + e.getMessage());
        }
    }

    private Map<String, Object> mockVerifyResponse(Long studentId) {
        Map<String, Object> packet = new HashMap<>();
        packet.put("sessionId", UUID.randomUUID().toString());
        packet.put("studentId", String.valueOf(studentId));
        packet.put("faceSimilarity", 0.88);
        packet.put("faceQuality", 0.84);
        packet.put("faceLiveness", 0.91);
        packet.put("faceSpoofProbability", 0.08);
        packet.put("voiceSimilarity", 0.0);
        packet.put("voiceSpoofProbability", 0.0);
        packet.put("challengeScore", 0.0);
        packet.put("behaviorScore", 0.0);
        packet.put("modelVersions", Map.of(
                "faceDetector", "scrfd-2.5g",
                "faceEmbedder", "arcface-r100",
                "faceSpoof", "minifasnet-v2",
                "speaker", "ecapa-tdnn",
                "voiceSpoof", "aasist-v1"
        ));
        packet.put("latencyMs", 120);

        Map<String, Object> response = new HashMap<>();
        response.put("match", true);
        response.put("liveness", true);
        response.put("scorePacket", packet);
        response.put("reasonCodes", List.of());
        response.put("contractVersion", "unified-score-packet/v1");
        return response;
    }

    private List<String> extractMlReasonCodes(Map<String, Object> verifyResponse) {
        Object obj = verifyResponse.get("reasonCodes");
        if (obj instanceof List<?> listObj) {
            List<String> parsed = new ArrayList<>();
            for (Object item : listObj) {
                if (item != null) {
                    parsed.add(String.valueOf(item));
                }
            }
            return parsed;
        }
        return List.of();
    }

    private Map<String, Object> buildFallbackScorePacket(String sessionId, Long studentId, Map<String, Object> verifyResponse) {
        Map<String, Object> packet = new HashMap<>();
        packet.put("sessionId", sessionId);
        packet.put("studentId", String.valueOf(studentId));

        boolean match = Boolean.TRUE.equals(verifyResponse.get("match"));
        boolean liveness = Boolean.TRUE.equals(verifyResponse.get("liveness"));

        packet.put("faceSimilarity", match ? 0.85 : 0.35);
        packet.put("faceQuality", 0.75);
        packet.put("faceLiveness", liveness ? 0.90 : 0.20);
        packet.put("faceSpoofProbability", liveness ? 0.10 : 0.80);

        packet.put("voiceSimilarity", 0.0);
        packet.put("voiceSpoofProbability", 0.0);
        packet.put("challengeScore", 0.0);
        packet.put("behaviorScore", 0.0);

        packet.put("modelVersions", Map.of(
                "faceDetector", "scrfd-2.5g",
                "faceEmbedder", "arcface-r100",
                "faceSpoof", "minifasnet-v2",
                "speaker", "pending",
                "voiceSpoof", "pending"
        ));
        packet.put("latencyMs", 0);
        return packet;
    }

    private boolean isFullMultimodalPacket(UnifiedScorePacketDTO packet) {
        return packet.getVoiceSimilarity() != null && packet.getVoiceSimilarity() > 0.0
                && packet.getChallengeScore() != null && packet.getChallengeScore() > 0.0
                && packet.getBehaviorScore() != null && packet.getBehaviorScore() > 0.0;
    }

    private double computeFullFusionScore(UnifiedScorePacketDTO packet) {
        double score = 0.30 * val(packet.getFaceSimilarity())
                + 0.15 * val(packet.getFaceQuality())
                + 0.20 * val(packet.getFaceLiveness())
                + 0.20 * val(packet.getVoiceSimilarity())
                + 0.10 * val(packet.getChallengeScore())
                + 0.05 * val(packet.getBehaviorScore())
                - 0.25 * val(packet.getFaceSpoofProbability())
                - 0.25 * val(packet.getVoiceSpoofProbability());
        return clamp01(score);
    }

    private double computeFaceOnlyFusionScore(UnifiedScorePacketDTO packet) {
        double score = 0.60 * val(packet.getFaceSimilarity())
                + 0.25 * val(packet.getFaceQuality())
                + 0.40 * val(packet.getFaceLiveness())
                - 0.25 * val(packet.getFaceSpoofProbability());
        return clamp01(score);
    }

    private Enums.IdentityDecision applyDecisionPolicy(
            UnifiedScorePacketDTO packet,
            double fusedScore,
            boolean fullMode,
            List<String> mlReasonCodes,
            List<String> reasonCodes
    ) {
        reasonCodes.addAll(mlReasonCodes);

        if (mlReasonCodes.contains("LIVENESS_FAILED")) {
            reasonCodes.add("POLICY_BLOCK_LIVENESS_FAILED");
            return Enums.IdentityDecision.BLOCK;
        }

        if (mlReasonCodes.contains("SINGLE_FACE_REQUIRED")) {
            reasonCodes.add("POLICY_BLOCK_MULTI_FACE");
            return Enums.IdentityDecision.BLOCK;
        }

        if (mlReasonCodes.contains("FACE_TOO_SMALL")
                || mlReasonCodes.contains("IMAGE_TOO_BLURRY")
                || mlReasonCodes.contains("BRIGHTNESS_OUT_OF_RANGE")) {
            if (fusedScore < 0.65) {
                reasonCodes.add("POLICY_BLOCK_QUALITY_GATE_AND_LOW_SCORE");
                return Enums.IdentityDecision.BLOCK;
            }
            reasonCodes.add("POLICY_STEP_UP_QUALITY_GATE");
            return Enums.IdentityDecision.STEP_UP;
        }

        double faceSpoof = val(packet.getFaceSpoofProbability());
        double voiceSpoof = val(packet.getVoiceSpoofProbability());

        if (faceSpoof >= 0.70) {
            reasonCodes.add("HARD_FACE_SPOOF_THRESHOLD");
            return Enums.IdentityDecision.BLOCK;
        }

        if (fullMode && voiceSpoof >= 0.70) {
            reasonCodes.add("HARD_VOICE_SPOOF_THRESHOLD");
            return Enums.IdentityDecision.BLOCK;
        }

        if (fullMode) {
            if (fusedScore >= 0.80 && faceSpoof < 0.30 && voiceSpoof < 0.30) {
                reasonCodes.add("FULL_MODE_ALLOW");
                return Enums.IdentityDecision.ALLOW;
            }
            if (fusedScore < 0.65) {
                reasonCodes.add("LOW_FUSED_SCORE");
                return Enums.IdentityDecision.BLOCK;
            }
            reasonCodes.add("MID_CONFIDENCE_STEP_UP");
            return Enums.IdentityDecision.STEP_UP;
        }

        reasonCodes.add("PARTIAL_MODALITY_FACE_ONLY");
        if (fusedScore < 0.55) {
            reasonCodes.add("FACE_ONLY_LOW_SCORE");
            return Enums.IdentityDecision.BLOCK;
        }
        reasonCodes.add("FACE_ONLY_NEVER_ALLOW_POLICY");
        reasonCodes.add("FACE_ONLY_STEP_UP");
        return Enums.IdentityDecision.STEP_UP;
    }

    private double val(Double input) {
        return input == null ? 0.0 : input;
    }

    private double clamp01(double value) {
        if (value < 0.0) {
            return 0.0;
        }
        if (value > 1.0) {
            return 1.0;
        }
        return value;
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize JSON payload: " + e.getMessage());
        }
    }

    private boolean hasHighRiskSignal(Map<String, Object> signals) {
        if (signals == null || signals.isEmpty()) {
            return false;
        }

        Object suspiciousEvents = signals.get("suspiciousEvents");
        if (suspiciousEvents instanceof Number number && number.intValue() >= 3) {
            return true;
        }

        Object faceMissing = signals.get("faceMissing");
        if (faceMissing instanceof Boolean b && b) {
            return true;
        }

        if (Boolean.TRUE.equals(signals.get("desktopViolation"))) {
            return true;
        }

        return false;
    }

    private void createIdentityAlert(
            IdentitySession session,
            List<String> reasonCodes,
            Map<String, Object> evidence,
            Enums.AlertSeverity severity,
            String category
    ) {
        IdentityAlert alert = new IdentityAlert();
        alert.setAlertId(UUID.randomUUID().toString());
        alert.setSession(session);
        alert.setSeverity(severity);
        alert.setCategory(category);
        alert.setEvidenceRef(toJson(Map.of(
                "reasonCodes", reasonCodes,
                "evidence", evidence
        )));
        alert.setResolved(false);
        identityAlertRepo.save(alert);
    }

    @SuppressWarnings("unchecked")
    private List<String> fromJsonArray(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(value, List.class);
        } catch (JsonProcessingException e) {
            return List.of("JSON_PARSE_ERROR");
        }
    }
}
