package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "screenshots")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Screenshot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
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
