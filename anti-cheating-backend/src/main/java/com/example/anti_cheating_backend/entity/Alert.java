package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

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
@Table(name = "alerts")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Alert {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id")
    private Event event;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id",nullable = false)
    private Student student;
    @Enumerated(EnumType.STRING)
    private Enums.AlertSeverity severity;
    @Column(nullable = false)
    private String message;
    @Column(columnDefinition = "TEXT")
    private String description;
    @Enumerated(EnumType.STRING)
    private Enums.AlertStatus status = Enums.AlertStatus.ACTIVE;
    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;
    @Column(name = "resolved_by")
    private String resolvedBy;
    private LocalDateTime timestamp;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_session_id")
    private ExamSession examSession;
}
