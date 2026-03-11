package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

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
    @JsonIgnoreProperties({"events", "enrollments", "password", "hibernateLazyInitializer", "handler"})
    private Student student;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
    @JsonIgnoreProperties({"events", "hibernateLazyInitializer", "handler"})
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
