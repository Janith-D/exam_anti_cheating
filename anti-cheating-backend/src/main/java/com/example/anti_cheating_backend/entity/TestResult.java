package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
@Table(name = "test_results")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class TestResult {

    public enum ResultStatus {
        GRADED,
        PENDING_REVIEW
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "student_id",nullable = false)
    private Student student;
    @ManyToOne
    @JoinColumn(name = "test_id",nullable = false)
    private Test test;
    private int correctAnswers;
    private double totalQuestions;
    private double scorePercentage;
    private LocalDateTime completedAt;

    @Column(length = 20)
    @Enumerated(EnumType.STRING)
    private ResultStatus status = ResultStatus.GRADED;

    @Column(columnDefinition = "TEXT")
    private String essayAnswersJson;
}
