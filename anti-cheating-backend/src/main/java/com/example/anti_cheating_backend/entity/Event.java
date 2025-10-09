package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "events")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id",nullable = false)
    private Student student;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
    private ExamSession examSession;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Enums.EventType type;
    @Column(columnDefinition = "TEXT")
    private String details;
    private LocalDateTime timestamp;
    private String browserInfo;
    private String ipAddress;
    private Integer severityLevel = 1;
    private Boolean isProcessed = false;
    private String snapshotPath;
    private Long duration; // e.g., for BLUR events
}
