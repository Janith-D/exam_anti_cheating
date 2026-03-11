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
import lombok.NoArgsConstructor;

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
    private Student student;
    
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "exam_session_id")
    @JsonIgnoreProperties({"events", "exam"})
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
}
