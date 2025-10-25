package com.example.anti_cheating_backend.dto;

import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.ExamSession;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AvailableExamDTO {
    private Long examId;
    private String title;
    private String description;
    private LocalDateTime startDate;
    private LocalDateTime endDate;
    private String status;
    private Double passingScore;
    private Integer maxStudents;
    
    // Active exam sessions for this exam
    private List<SessionInfo> activeSessions;
    
    // Enrollment status
    private Boolean isEnrolled;
    private String enrollmentStatus;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SessionInfo {
        private Long sessionId;
        private String examName;
        private LocalDateTime startTime;
        private LocalDateTime endTime;
        private Integer durationMinutes;
        private String status;
    }
    
    // Constructor from Exam entity
    public AvailableExamDTO(Exam exam) {
        this.examId = exam.getId();
        this.title = exam.getTitle();
        this.description = exam.getDescription();
        this.startDate = exam.getStartDate();
        this.endDate = exam.getEndDate();
        this.status = exam.getStatus() != null ? exam.getStatus().name() : null;
        this.passingScore = exam.getPassingScore();
        this.maxStudents = exam.getMaxStudents();
    }
    
    // Add session info
    public void addSession(ExamSession session) {
        SessionInfo sessionInfo = new SessionInfo(
            session.getId(),
            session.getExamName(),
            session.getStartTime(),
            session.getEndTime(),
            session.getDurationMinutes(),
            session.getStatus() != null ? session.getStatus().name() : null
        );
        if (this.activeSessions == null) {
            this.activeSessions = new java.util.ArrayList<>();
        }
        this.activeSessions.add(sessionInfo);
    }
}
