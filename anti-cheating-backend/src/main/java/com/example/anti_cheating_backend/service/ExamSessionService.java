package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.repo.ExamRepo;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;

@Service
public class ExamSessionService {

    @Autowired
    private ExamSessionRepo examSessionRepo;
    
    @Autowired
    private ExamRepo examRepo;

    // Get all sessions
    public List<ExamSession> getAllSessions() {
        return examSessionRepo.findAll();
    }

    // Get session by ID
    public ExamSession getSessionById(Long sessionId) {
        return examSessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
    }

    // Create session - updated to accept examId
    public ExamSession createSession(String examName, LocalDateTime startTime, LocalDateTime endTime, String createdBy){
        ExamSession session = new ExamSession();
        session.setExamName(examName);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setCreatedBy(createdBy);
        session.setDurationMinutes((int)java.time.Duration.between(startTime,endTime).toMinutes());
        return examSessionRepo.save(session);
    }
    
    // Create session with exam relationship
    public ExamSession createSessionWithExam(Long examId, LocalDateTime startTime, LocalDateTime endTime, String createdBy){
        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found"));
        
        ExamSession session = new ExamSession();
        session.setExamName(exam.getTitle());
        session.setExam(exam);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setCreatedBy(createdBy);
        session.setDurationMinutes((int)java.time.Duration.between(startTime,endTime).toMinutes());
        return examSessionRepo.save(session);
    }
    
    public List<ExamSession> getActiveSessions(){
        return examSessionRepo.findByStatus(Enums.SessionStatus.ACTIVE);
    }

    // Update session
    public ExamSession updateSession(Long sessionId, java.util.Map<String, Object> payload) {
        ExamSession session = examSessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        
        if (payload.containsKey("examName")) {
            session.setExamName((String) payload.get("examName"));
        }
        if (payload.containsKey("startTime")) {
            LocalDateTime startTime = LocalDateTime.parse((String) payload.get("startTime"));
            session.setStartTime(startTime);
        }
        if (payload.containsKey("endTime")) {
            LocalDateTime endTime = LocalDateTime.parse((String) payload.get("endTime"));
            session.setEndTime(endTime);
        }
        
        // Recalculate duration if times changed
        if (session.getStartTime() != null && session.getEndTime() != null) {
            session.setDurationMinutes((int) java.time.Duration.between(
                session.getStartTime(), session.getEndTime()).toMinutes());
        }
        
        return examSessionRepo.save(session);
    }

    // Delete session
    public void deleteSession(Long sessionId) {
        ExamSession session = examSessionRepo.findById(sessionId)
                .orElseThrow(() -> new RuntimeException("Session not found"));
        examSessionRepo.delete(session);
    }

    // Start session
    public ExamSession startSession(Long sessionId){
        ExamSession session = examSessionRepo.findById(sessionId)
                .orElseThrow(()-> new RuntimeException("Session not found"));
        session.setStatus(Enums.SessionStatus.ACTIVE);
        return examSessionRepo.save(session);
    }

    public ExamSession endSession(Long sessionId){
        ExamSession session = examSessionRepo.findById(sessionId)
                .orElseThrow(()-> new RuntimeException("Session not found"));
        session.setStatus(Enums.SessionStatus.COMPLETED);
        return examSessionRepo.save(session);
    }
    
    public java.util.Optional<ExamSession> findById(Long sessionId){
        return examSessionRepo.findById(sessionId);
    }

    // Get or create session for a test (when student starts test)
    public ExamSession getOrCreateSessionForTest(Long testId) {
        // Look for an active session for this test
        List<ExamSession> activeSessions = examSessionRepo.findByStatus(Enums.SessionStatus.ACTIVE);
        
        // Check if there's an active session for this test
        java.util.Optional<ExamSession> existingSession = activeSessions.stream()
            .filter(session -> session.getExamName() != null && 
                               session.getExamName().contains("Test #" + testId))
            .findFirst();
        
        if (existingSession.isPresent()) {
            return existingSession.get();
        }
        
        // Create new session for this test
        ExamSession newSession = new ExamSession();
        newSession.setExamName("Test #" + testId);
        newSession.setStartTime(LocalDateTime.now());
        newSession.setStatus(Enums.SessionStatus.ACTIVE);
        newSession.setCreatedBy("SYSTEM");
        
        return examSessionRepo.save(newSession);
    }
}
