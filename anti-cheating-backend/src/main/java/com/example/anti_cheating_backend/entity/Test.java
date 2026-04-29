package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

import com.example.anti_cheating_backend.entity.Enums.TestType;

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
    
    // Many tests belong to many exams (ManyToMany relationship)
    @ManyToMany(mappedBy = "tests")
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Exam> exams;

    // One test can have multiple questions (cascade delete)
    @OneToMany(mappedBy = "test", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private List<Question> questions;
    
    @Column(name = "test_order")
    private Integer testOrder; // Order of test within the exam
    
    @Column(name = "passing_score")
    private Double passingScore;
    
    @Column(name = "total_marks")
    private Double totalMarks;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "test_type", length = 20)
    private TestType type = TestType.MCQ;
}
