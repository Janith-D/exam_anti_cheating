package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.service.ExamSessionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/sessions")
public class ExamSessionController {

    @Autowired
    private ExamSessionService examSessionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSession(@RequestBody Map<String, Object> payload) {
        try {
            String examName = (String) payload.get("examName");
            LocalDateTime startTime = LocalDateTime.parse((String) payload.get("startTime"));
            LocalDateTime endTime = LocalDateTime.parse((String) payload.get("endTime"));
            String createdBy = (String) payload.get("createdBy");
            ExamSession session = examSessionService.createSession(examName, startTime, endTime, createdBy);
            return ResponseEntity.ok(Map.of("message", "Session created", "sessionId", session.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<List<ExamSession>> getActiveSessions() {
        return ResponseEntity.ok(examSessionService.getActiveSessions());
    }

    @PutMapping("/start/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> startSession(@PathVariable Long sessionId) {
        try {
            ExamSession session = examSessionService.startSession(sessionId);
            return ResponseEntity.ok(Map.of("message", "Session started", "sessionId", session.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/end/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> endSession(@PathVariable Long sessionId) {
        try {
            ExamSession session = examSessionService.endSession(sessionId);
            return ResponseEntity.ok(Map.of("message", "Session ended", "sessionId", session.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
