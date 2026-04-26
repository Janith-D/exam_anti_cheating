package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "screenshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
public class Screenshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"password", "events", "enrollments", "createdAt", "updatedAt"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private Student student;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exam_session_id")
    @JsonIgnoreProperties({"events", "exam"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private ExamSession examSession;
    
    @Column(nullable = false)
    private String filePath;
    
    @Column(nullable = false)
    private LocalDateTime timestamp;
    
    @Column(length = 1000)
    private String activeWindow;
    
    @Column(length = 1000)
    private String runningProcesses;
    
    @Column(length = 500)
    private String captureSource; // "desktop", "webcam", etc.
    
    private Boolean flaggedSuspicious = false;
    
    @Column(length = 1000)
    private String suspiciousReason;
    
    @PrePersist
    protected void onCreate() {
        timestamp = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Student getStudent() {
        return student;
    }

    public void setStudent(Student student) {
        this.student = student;
    }

    public ExamSession getExamSession() {
        return examSession;
    }

    public void setExamSession(ExamSession examSession) {
        this.examSession = examSession;
    }

    public String getFilePath() {
        return filePath;
    }

    public void setFilePath(String filePath) {
        this.filePath = filePath;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public String getActiveWindow() {
        return activeWindow;
    }

    public void setActiveWindow(String activeWindow) {
        this.activeWindow = activeWindow;
    }

    public String getRunningProcesses() {
        return runningProcesses;
    }

    public void setRunningProcesses(String runningProcesses) {
        this.runningProcesses = runningProcesses;
    }

    public String getCaptureSource() {
        return captureSource;
    }

    public void setCaptureSource(String captureSource) {
        this.captureSource = captureSource;
    }

    public Boolean getFlaggedSuspicious() {
        return flaggedSuspicious;
    }

    public void setFlaggedSuspicious(Boolean flaggedSuspicious) {
        this.flaggedSuspicious = flaggedSuspicious;
    }

    public String getSuspiciousReason() {
        return suspiciousReason;
    }

    public void setSuspiciousReason(String suspiciousReason) {
        this.suspiciousReason = suspiciousReason;
    }
}
