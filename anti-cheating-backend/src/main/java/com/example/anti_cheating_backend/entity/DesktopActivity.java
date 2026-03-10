package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "desktop_activity")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class DesktopActivity {
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
    private LocalDateTime timestamp;
    
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType activityType;
    
    @Column(length = 2000)
    private String details;
    
    @Column(length = 500)
    private String activeWindow;
    
    @Column(length = 500)
    private String applicationName;
    
    private Integer severityLevel = 1; // 1-5, where 5 is most suspicious
    
    private Boolean isProcessed = false;
    
    public enum ActivityType {
        WINDOW_SWITCH,
        APPLICATION_LAUNCHED,
        APPLICATION_CLOSED,
        DESKTOP_IDLE,
        SUSPICIOUS_APP_DETECTED,
        EXAM_WINDOW_MINIMIZED,
        EXAM_WINDOW_RESTORED,
        SCREENSHOT_CAPTURED,
        MONITORING_STARTED,
        MONITORING_STOPPED
    }
    
    @PrePersist
    protected void onCreate() {
        if (timestamp == null) {
            timestamp = LocalDateTime.now();
        }
    }
}
