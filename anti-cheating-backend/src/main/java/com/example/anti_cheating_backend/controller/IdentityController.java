package com.example.anti_cheating_backend.controller;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.dto.identity.IdentityEnrollBehaviorRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityEnrollFaceRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityEnrollFinalizeRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityEnrollStartRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityEnrollVoiceRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityMonitorPingRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityMonitorRecheckRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityProctorOverrideRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityVerifyStartRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityVerifyStepupRequest;
import com.example.anti_cheating_backend.dto.identity.IdentityVerifySubmitRequest;
import com.example.anti_cheating_backend.service.IdentityEnrollmentService;
import com.example.anti_cheating_backend.service.IdentityVerificationService;

@RestController
@RequestMapping({"/api/identity", "/identity"})
public class IdentityController {

    @Autowired
    private IdentityVerificationService identityVerificationService;

    @Autowired
    private IdentityEnrollmentService identityEnrollmentService;

    @PostMapping("/enroll/start")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> startEnroll(@RequestBody IdentityEnrollStartRequest request) {
        try {
            Map<String, Object> response = identityEnrollmentService.startEnrollment(request.getStudentId());
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/enroll/face")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> enrollFace(@RequestBody IdentityEnrollFaceRequest request) {
        try {
            Map<String, Object> response = identityEnrollmentService.enrollFace(
                    request.getEnrollmentToken(),
                    request.getImage()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/enroll/voice")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> enrollVoice(@RequestBody IdentityEnrollVoiceRequest request) {
        try {
            Map<String, Object> response = identityEnrollmentService.enrollVoice(
                    request.getEnrollmentToken(),
                    request.getAudio()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/enroll/behavior")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> enrollBehavior(@RequestBody IdentityEnrollBehaviorRequest request) {
        try {
            Map<String, Object> response = identityEnrollmentService.enrollBehavior(
                    request.getEnrollmentToken(),
                    request.getFeatures()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/enroll/finalize")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> finalizeEnroll(@RequestBody IdentityEnrollFinalizeRequest request) {
        try {
            Map<String, Object> response = identityEnrollmentService.finalizeEnrollment(
                    request.getEnrollmentToken()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/enroll/status/{enrollmentToken}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> enrollStatus(@PathVariable String enrollmentToken) {
        try {
            Map<String, Object> response = identityEnrollmentService.getEnrollmentStatus(enrollmentToken);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify/start")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> startVerify(@RequestBody IdentityVerifyStartRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.startVerificationSession(
                    request.getStudentId(),
                    request.getExamSessionId(),
                    request.getTtlSeconds()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify/submit")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> submitVerify(@RequestBody IdentityVerifySubmitRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.submitVerification(
                    request.getSessionId(),
                    request.getStudentId(),
                    request.getImage()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/verify/result/{sessionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> getVerifyResult(@PathVariable String sessionId) {
        try {
            Map<String, Object> response = identityVerificationService.getVerificationResult(sessionId);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify/stepup")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> requestStepup(@RequestBody IdentityVerifyStepupRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.requestStepUp(
                    request.getSessionId(),
                    request.getTrigger()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/monitor/ping")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> monitorPing(@RequestBody IdentityMonitorPingRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.monitorPing(
                    request.getSessionId(),
                    request.getSignals()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/monitor/recheck")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN', 'PROCTOR')")
    public ResponseEntity<?> monitorRecheck(@RequestBody IdentityMonitorRecheckRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.monitorRecheck(
                    request.getSessionId(),
                    request.getStudentId(),
                    request.getImage()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/proctor/override")
    @PreAuthorize("hasAnyRole('ADMIN', 'PROCTOR')")
    public ResponseEntity<?> proctorOverride(@RequestBody IdentityProctorOverrideRequest request) {
        try {
            Map<String, Object> response = identityVerificationService.proctorOverride(
                    request.getSessionId(),
                    request.getDecision(),
                    request.getReason(),
                    request.getProctorId()
            );
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
