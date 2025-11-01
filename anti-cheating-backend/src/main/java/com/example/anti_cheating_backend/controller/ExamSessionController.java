package com.example.anti_cheating_backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.service.ExamSessionService;

@RestController
@RequestMapping("/api/sessions")
public class ExamSessionController {

    @Autowired
    private ExamSessionService examSessionService;

    // Get all exam sessions
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<ExamSession>> getAllSessions() {
        List<ExamSession> sessions = examSessionService.getAllSessions();
        
        // Validate exam relationships to prevent serialization errors
        sessions.forEach(session -> {
            try {
                if (session.getExam() != null) {
                    session.getExam().getId(); // Trigger lazy load
                }
            } catch (Exception e) {
                System.err.println("Warning: Session " + session.getId() + " has invalid exam reference");
                session.setExam(null); // Clear invalid reference
            }
        });
        
        return ResponseEntity.ok(sessions);
    }

    // Get exam session by ID
    @GetMapping("/{sessionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getSessionById(@PathVariable Long sessionId) {
        try {
            ExamSession session = examSessionService.getSessionById(sessionId);
            
            // Handle potential lazy loading issues with exam relationship
            try {
                if (session.getExam() != null) {
                    // Trigger lazy load to validate exam exists
                    session.getExam().getId();
                }
            } catch (Exception e) {
                System.err.println("Warning: Session " + sessionId + " has invalid exam reference: " + e.getMessage());
                // Continue - session is still valid without exam
            }
            
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Create exam session
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createSession(@RequestBody Map<String, Object> payload) {
        try {
            // Check if examId is provided (new approach with exam relationship)
            if (payload.containsKey("examId")) {
                Long examId = ((Number) payload.get("examId")).longValue();
                LocalDateTime startTime = LocalDateTime.parse((String) payload.get("startTime"));
                LocalDateTime endTime = LocalDateTime.parse((String) payload.get("endTime"));
                String createdBy = (String) payload.get("createdBy");
                ExamSession session = examSessionService.createSessionWithExam(examId, startTime, endTime, createdBy);
                return ResponseEntity.ok(Map.of("message", "Session created", "sessionId", session.getId()));
            } 
            // Fallback to old approach for backward compatibility
            else {
                String examName = (String) payload.get("examName");
                LocalDateTime startTime = LocalDateTime.parse((String) payload.get("startTime"));
                LocalDateTime endTime = LocalDateTime.parse((String) payload.get("endTime"));
                String createdBy = (String) payload.get("createdBy");
                ExamSession session = examSessionService.createSession(examName, startTime, endTime, createdBy);
                return ResponseEntity.ok(Map.of("message", "Session created", "sessionId", session.getId()));
            }
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/active")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<List<ExamSession>> getActiveSessions() {
        List<ExamSession> sessions = examSessionService.getActiveSessions();
        
        // Validate exam relationships to prevent serialization errors
        sessions.forEach(session -> {
            try {
                if (session.getExam() != null) {
                    session.getExam().getId(); // Trigger lazy load
                }
            } catch (Exception e) {
                System.err.println("Warning: Active session " + session.getId() + " has invalid exam reference");
                session.setExam(null); // Clear invalid reference
            }
        });
        
        return ResponseEntity.ok(sessions);
    }

    // Update exam session
    @PutMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateSession(@PathVariable Long sessionId, @RequestBody Map<String, Object> payload) {
        try {
            ExamSession session = examSessionService.updateSession(sessionId, payload);
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete exam session
    @DeleteMapping("/{sessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteSession(@PathVariable Long sessionId) {
        try {
            examSessionService.deleteSession(sessionId);
            return ResponseEntity.ok(Map.of("message", "Session deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Start exam session
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

    // Get or create exam session for a test (when student starts test)
    @PostMapping("/test/{testId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getOrCreateSessionForTest(@PathVariable Long testId) {
        try {
            ExamSession session = examSessionService.getOrCreateSessionForTest(testId);
            return ResponseEntity.ok(session);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
