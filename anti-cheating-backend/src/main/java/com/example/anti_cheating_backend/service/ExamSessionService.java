package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;

@Service
public class ExamSessionService {

    @Autowired
    private ExamSessionRepo examSessionRepo;

    public ExamSession createSession(String examName, LocalDateTime startTime,LocalDateTime endTime, String createdBy){
        ExamSession session = new ExamSession();
        session.setExamName(examName);
        session.setStartTime(startTime);
        session.setEndTime(endTime);
        session.setCreatedBy(createdBy);
        session.setDurationMinutes((int)java.time.Duration.between(startTime,endTime).toMinutes());
        return examSessionRepo.save(session);
    }
    public List<ExamSession> getActiveSessions(){
        return examSessionRepo.findByStatus(Enums.SessionStatus.ACTIVE);
    }
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
}
