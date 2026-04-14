package com.example.anti_cheating_backend.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.anti_cheating_backend.entity.BiometricProfile;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.IdentityEnrollmentSession;
import com.example.anti_cheating_backend.repo.BiometricProfileRepo;
import com.example.anti_cheating_backend.repo.IdentityEnrollmentSessionRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

@Service
public class IdentityEnrollmentService {

    private static final Logger LOGGER = Logger.getLogger(IdentityEnrollmentService.class.getName());

    @Autowired
    private StudentRepo studentRepo;

    @Autowired
    private BiometricProfileRepo biometricProfileRepo;

    @Autowired
    private IdentityEnrollmentSessionRepo identityEnrollmentSessionRepo;

    @Autowired
    private ObjectMapper objectMapper;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceUrl;

    @Value("${ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    @Value("${identity.enroll.ttl.seconds:900}")
    private int enrollTtlSeconds;

    @Value("${identity.enroll.cleanup.delay.ms:60000}")
    private long enrollCleanupDelayMs;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> startEnrollment(Long studentId) {
        if (studentId == null) {
            throw new RuntimeException("studentId is required");
        }
        if (!studentRepo.existsById(studentId)) {
            throw new RuntimeException("Student not found: " + studentId);
        }

        String token = UUID.randomUUID().toString();
        LocalDateTime now = LocalDateTime.now();

        IdentityEnrollmentSession session = new IdentityEnrollmentSession();
        session.setEnrollmentToken(token);
        session.setStudentId(studentId);
        session.setState(Enums.IdentityEnrollmentState.ACTIVE);
        session.setStartedAt(now);
        session.setExpiresAt(now.plusSeconds(Math.max(enrollTtlSeconds, 300)));
        session.setQualityMetaJson(toJson(new HashMap<>()));
        session.setModelVersionsJson(toJson(new HashMap<>()));
        session = identityEnrollmentSessionRepo.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("enrollmentToken", token);
        response.put("studentId", studentId);
        response.put("startedAt", session.getStartedAt());
        response.put("expiresAt", session.getExpiresAt());
        response.put("requiredModalities", List.of("face", "voice", "behavior"));
        response.put("status", "ENROLLMENT_STARTED");
        return response;
    }

    public Map<String, Object> enrollFace(String enrollmentToken, String image) {
        if (image == null || image.isBlank()) {
            throw new RuntimeException("image is required");
        }

        IdentityEnrollmentSession session = getActiveSession(enrollmentToken);
        Map<String, Object> mlResponse = callMlEnroll(session.getStudentId(), image);

        Object embeddingObj = mlResponse.get("embedding");
        String faceTemplate = embeddingObj == null || String.valueOf(embeddingObj).isBlank()
                ? buildDeterministicTemplate("face", image)
                : String.valueOf(embeddingObj);

        Map<String, Object> qualityMeta = fromJsonMap(session.getQualityMetaJson());
        Map<String, Object> modelVersions = fromJsonMap(session.getModelVersionsJson());

        session.setFaceTemplate(faceTemplate);

        Object qualityObj = mlResponse.get("quality");
        if (qualityObj instanceof Number quality) {
            qualityMeta.put("faceQuality", quality.doubleValue());
        }
        Object reasonCodes = mlResponse.get("reasonCodes");
        if (reasonCodes instanceof List<?> list) {
            qualityMeta.put("faceReasonCodes", list);
        }
        modelVersions.put("faceEnrollment", String.valueOf(mlResponse.getOrDefault("model", "arcface-r100")));

        session.setQualityMetaJson(toJson(qualityMeta));
        session.setModelVersionsJson(toJson(modelVersions));
        session = identityEnrollmentSessionRepo.save(session);

        return buildModalityResponse(session, "FACE_ENROLLED");
    }

    public Map<String, Object> enrollVoice(String enrollmentToken, String audio) {
        if (audio == null || audio.isBlank()) {
            throw new RuntimeException("audio is required");
        }

        IdentityEnrollmentSession session = getActiveSession(enrollmentToken);
        Map<String, Object> mlResponse = callMlVoiceEnroll(session.getStudentId(), audio);
        Map<String, Object> qualityMeta = fromJsonMap(session.getQualityMetaJson());
        Map<String, Object> modelVersions = fromJsonMap(session.getModelVersionsJson());

        Object templateObj = mlResponse.get("voiceTemplate");
        String voiceTemplate = templateObj == null || String.valueOf(templateObj).isBlank()
                ? buildDeterministicTemplate("voice", audio)
                : String.valueOf(templateObj);

        session.setVoiceTemplate(voiceTemplate);
        qualityMeta.put("voiceSampleChars", audio.length());

        Object voiceQuality = mlResponse.get("voiceQuality");
        if (voiceQuality instanceof Number qualityNumber) {
            qualityMeta.put("voiceQuality", qualityNumber.doubleValue());
        }
        Object voiceSpoofProbability = mlResponse.get("voiceSpoofProbability");
        if (voiceSpoofProbability instanceof Number spoofNumber) {
            qualityMeta.put("voiceSpoofProbability", spoofNumber.doubleValue());
        }
        Object reasonCodes = mlResponse.get("reasonCodes");
        if (reasonCodes instanceof List<?> reasonList) {
            qualityMeta.put("voiceReasonCodes", reasonList);
        }

        modelVersions.put("voiceEnrollment", String.valueOf(mlResponse.getOrDefault("model", "ecapa-tdnn-fallback")));

        session.setQualityMetaJson(toJson(qualityMeta));
        session.setModelVersionsJson(toJson(modelVersions));
        session = identityEnrollmentSessionRepo.save(session);

        return buildModalityResponse(session, "VOICE_ENROLLED");
    }

    public Map<String, Object> enrollBehavior(String enrollmentToken, Map<String, Object> features) {
        if (features == null || features.isEmpty()) {
            throw new RuntimeException("features are required");
        }

        IdentityEnrollmentSession session = getActiveSession(enrollmentToken);
        Map<String, Object> qualityMeta = fromJsonMap(session.getQualityMetaJson());
        Map<String, Object> modelVersions = fromJsonMap(session.getModelVersionsJson());

        String featureJson = toJson(features);
        session.setBehaviorTemplate(buildDeterministicTemplate("behavior", featureJson));
        qualityMeta.put("behaviorFeatureCount", features.size());
        modelVersions.put("behaviorEnrollment", "behavior-baseline-v1");

        session.setQualityMetaJson(toJson(qualityMeta));
        session.setModelVersionsJson(toJson(modelVersions));
        session = identityEnrollmentSessionRepo.save(session);

        return buildModalityResponse(session, "BEHAVIOR_ENROLLED");
    }

    public Map<String, Object> finalizeEnrollment(String enrollmentToken) {
        IdentityEnrollmentSession session = getActiveSession(enrollmentToken);

        List<String> missing = collectMissingModalities(session);
        if (!missing.isEmpty()) {
            throw new RuntimeException("Cannot finalize enrollment. Missing modalities: " + String.join(", ", missing));
        }

        BiometricProfile profile = biometricProfileRepo.findByStudentId(session.getStudentId()).orElseGet(BiometricProfile::new);
        profile.setStudentId(session.getStudentId());
        profile.setFaceTemplate(session.getFaceTemplate());
        profile.setVoiceTemplate(session.getVoiceTemplate());
        profile.setBehaviorTemplate(session.getBehaviorTemplate());
        profile.setQualityMeta(session.getQualityMetaJson());
        profile.setModelVersions(session.getModelVersionsJson());

        profile = biometricProfileRepo.save(profile);
        session.setState(Enums.IdentityEnrollmentState.COMPLETED);
        session.setCompletedAt(LocalDateTime.now());
        identityEnrollmentSessionRepo.save(session);

        Map<String, Object> response = new HashMap<>();
        response.put("enrollmentToken", session.getEnrollmentToken());
        response.put("profileId", profile.getId());
        response.put("studentId", profile.getStudentId());
        response.put("status", "ENROLLMENT_COMPLETED");
        response.put("enrolledModalities", List.of("face", "voice", "behavior"));
        response.put("completedAt", session.getCompletedAt());
        response.put("updatedAt", profile.getUpdatedAt());
        return response;
    }

    public Map<String, Object> getEnrollmentStatus(String enrollmentToken) {
        if (enrollmentToken == null || enrollmentToken.isBlank()) {
            throw new RuntimeException("enrollmentToken is required");
        }

        IdentityEnrollmentSession session = identityEnrollmentSessionRepo.findById(enrollmentToken)
                .orElseThrow(() -> new RuntimeException("Enrollment session not found: " + enrollmentToken));

        // Keep state accurate for clients polling status.
        if (session.getState() == Enums.IdentityEnrollmentState.ACTIVE && LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setState(Enums.IdentityEnrollmentState.EXPIRED);
            session = identityEnrollmentSessionRepo.save(session);
        }

        Map<String, Object> response = new HashMap<>();
        response.put("enrollmentToken", session.getEnrollmentToken());
        response.put("studentId", session.getStudentId());
        response.put("state", session.getState());
        response.put("startedAt", session.getStartedAt());
        response.put("expiresAt", session.getExpiresAt());
        response.put("completedAt", session.getCompletedAt());
        response.put("completedModalities", collectCompletedModalities(session));
        response.put("missingModalities", collectMissingModalities(session));
        response.put("qualityMeta", fromJsonMap(session.getQualityMetaJson()));
        response.put("modelVersions", fromJsonMap(session.getModelVersionsJson()));
        return response;
    }

    @Scheduled(initialDelayString = "${identity.enroll.cleanup.delay.ms:60000}", fixedDelayString = "${identity.enroll.cleanup.delay.ms:60000}")
    public void expireStaleEnrollmentSessions() {
        LocalDateTime now = LocalDateTime.now();
        List<IdentityEnrollmentSession> staleActiveSessions = identityEnrollmentSessionRepo
                .findByStateAndExpiresAtBefore(Enums.IdentityEnrollmentState.ACTIVE, now);

        if (staleActiveSessions.isEmpty()) {
            return;
        }

        for (IdentityEnrollmentSession session : staleActiveSessions) {
            session.setState(Enums.IdentityEnrollmentState.EXPIRED);
        }
        identityEnrollmentSessionRepo.saveAll(staleActiveSessions);

        LOGGER.log(
            Level.INFO,
            "Expired enrollment sessions: {0} (cleanup interval ms={1})",
            new Object[]{staleActiveSessions.size(), enrollCleanupDelayMs}
        );
    }

    private Map<String, Object> buildModalityResponse(IdentityEnrollmentSession session, String status) {
        Map<String, Object> response = new HashMap<>();
        response.put("enrollmentToken", session.getEnrollmentToken());
        response.put("studentId", session.getStudentId());
        response.put("status", status);
        response.put("completedModalities", collectCompletedModalities(session));
        response.put("missingModalities", collectMissingModalities(session));
        response.put("expiresAt", session.getExpiresAt());
        return response;
    }

    private IdentityEnrollmentSession getActiveSession(String enrollmentToken) {
        if (enrollmentToken == null || enrollmentToken.isBlank()) {
            throw new RuntimeException("enrollmentToken is required");
        }

        IdentityEnrollmentSession session = identityEnrollmentSessionRepo.findById(enrollmentToken)
                .orElseThrow(() -> new RuntimeException("Enrollment session not found: " + enrollmentToken));

        if (session.getState() == Enums.IdentityEnrollmentState.COMPLETED) {
            throw new RuntimeException("Enrollment session already finalized");
        }

        if (session.getState() == Enums.IdentityEnrollmentState.EXPIRED || LocalDateTime.now().isAfter(session.getExpiresAt())) {
            session.setState(Enums.IdentityEnrollmentState.EXPIRED);
            identityEnrollmentSessionRepo.save(session);
            throw new RuntimeException("Enrollment session expired");
        }

        return session;
    }

    private List<String> collectCompletedModalities(IdentityEnrollmentSession session) {
        List<String> completed = new java.util.ArrayList<>();
        if (session.getFaceTemplate() != null && !session.getFaceTemplate().isBlank()) {
            completed.add("face");
        }
        if (session.getVoiceTemplate() != null && !session.getVoiceTemplate().isBlank()) {
            completed.add("voice");
        }
        if (session.getBehaviorTemplate() != null && !session.getBehaviorTemplate().isBlank()) {
            completed.add("behavior");
        }
        return completed;
    }

    private List<String> collectMissingModalities(IdentityEnrollmentSession session) {
        List<String> missing = new java.util.ArrayList<>();
        if (session.getFaceTemplate() == null || session.getFaceTemplate().isBlank()) {
            missing.add("face");
        }
        if (session.getVoiceTemplate() == null || session.getVoiceTemplate().isBlank()) {
            missing.add("voice");
        }
        if (session.getBehaviorTemplate() == null || session.getBehaviorTemplate().isBlank()) {
            missing.add("behavior");
        }
        return missing;
    }

    private Map<String, Object> fromJsonMap(String rawJson) {
        if (rawJson == null || rawJson.isBlank()) {
            return new HashMap<>();
        }

        try {
            Map<String, Object> parsed = objectMapper.readValue(rawJson, new TypeReference<Map<String, Object>>() {});
            return parsed == null ? new HashMap<>() : new HashMap<>(parsed);
        } catch (JsonProcessingException e) {
            return new HashMap<>();
        }
    }

    private Map<String, Object> callMlEnroll(Long studentId, String image) {
        Map<String, Object> payload = Map.of(
                "studentId", studentId,
                "image", image
        );

        if (!mlServiceEnabled) {
            LOGGER.info("ML service disabled; using deterministic face template for enrollment");
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("embedding", buildDeterministicTemplate("face", image));
            fallback.put("quality", 0.9);
            fallback.put("model", "arcface-r100");
            fallback.put("reasonCodes", List.of());
            return fallback;
        }

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    mlServiceUrl + "/enroll",
                    payload,
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("ML enroll request failed with status: " + response.getStatusCode());
            }
            if (!(response.getBody() instanceof Map<?, ?> bodyMap)) {
                throw new RuntimeException("ML enroll response is not a JSON object");
            }
            return objectMapper.convertValue(bodyMap, new TypeReference<Map<String, Object>>() {});
        } catch (RestClientException ex) {
            throw new RuntimeException("ML enroll call failed: " + ex.getMessage());
        }
    }

    private Map<String, Object> callMlVoiceEnroll(Long studentId, String audio) {
        Map<String, Object> payload = Map.of(
                "studentId", studentId,
                "audio", audio
        );

        if (!mlServiceEnabled) {
            Map<String, Object> fallback = new LinkedHashMap<>();
            fallback.put("voiceTemplate", buildDeterministicTemplate("voice", audio));
            fallback.put("voiceQuality", 0.70);
            fallback.put("voiceSpoofProbability", 0.30);
            fallback.put("model", "ecapa-tdnn-fallback");
            fallback.put("reasonCodes", List.of("VOICE_FALLBACK_MODE"));
            return fallback;
        }

        try {
            ResponseEntity<Object> response = restTemplate.postForEntity(
                    mlServiceUrl + "/voice/enroll",
                    payload,
                    Object.class
            );
            if (!response.getStatusCode().is2xxSuccessful() || response.getBody() == null) {
                throw new RuntimeException("ML voice enroll request failed with status: " + response.getStatusCode());
            }
            if (!(response.getBody() instanceof Map<?, ?> bodyMap)) {
                throw new RuntimeException("ML voice enroll response is not a JSON object");
            }
            return objectMapper.convertValue(bodyMap, new TypeReference<Map<String, Object>>() {});
        } catch (RestClientException ex) {
            throw new RuntimeException("ML voice enroll call failed: " + ex.getMessage());
        }
    }

    private String buildDeterministicTemplate(String prefix, String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hashed = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            return prefix + ":" + Base64.getEncoder().encodeToString(hashed);
        } catch (NoSuchAlgorithmException ex) {
            throw new RuntimeException("Unable to generate template hash", ex);
        }
    }

    private String toJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return String.valueOf(value);
        }
    }
}
