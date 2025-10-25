package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "tests")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Test {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    private String title;
    
    private String description;
    
    private String createdBy;
    
    private LocalDateTime createdAt;
    
    private int duration; // in minutes
    
    // Many tests belong to one exam
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id")
    private Exam exam;
    
    @Column(name = "test_order")
    private Integer testOrder; // Order of test within the exam
    
    @Column(name = "passing_score")
    private Double passingScore;
    
    @Column(name = "total_marks")
    private Double totalMarks;
}
