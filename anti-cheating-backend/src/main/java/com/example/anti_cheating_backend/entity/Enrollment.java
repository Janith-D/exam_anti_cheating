package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIdentityInfo;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.ObjectIdGenerators;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties({"hibernateLazyInitializer", "handler"})
@JsonIdentityInfo(generator = ObjectIdGenerators.PropertyGenerator.class, property = "id")
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    @JsonIgnoreProperties({"enrollments", "events", "password", "hibernateLazyInitializer", "handler"})
    private Student student;
    
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exam_id", nullable = true)
    @JsonIgnoreProperties({"tests", "enrollments", "hibernateLazyInitializer", "handler"})
    private Exam exam;
    
    @Column(name = "face_embedding", columnDefinition = "TEXT")
    private String faceEmbedding;
    
    @Column(name = "enrollment_images", columnDefinition = "TEXT")
    private String enrollmentImages;
    
    @Column(name = "verification_score")
    private Double verificationScore;
    
    @Column(name = "is_verified")
    private Boolean isVerified = false;
    
    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;
    
    @Column(name = "last_verification")
    private LocalDateTime lastVerification;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Enums.EnrollmentStatus status = Enums.EnrollmentStatus.PENDING;

    @Column(name = "is_blocked")
    private Boolean isBlocked = false;
    
    @Column(name = "blocked_at")
    private LocalDateTime blockedAt;
    
    @Column(name = "blocked_by")
    private String blockedBy;
    
    @Column(name = "block_reason")
    private String blockReason;
    
    @Column(name = "unblocked_at")
    private LocalDateTime unblockedAt;
    
    @Column(name = "unblocked_by")
    private String unblockedBy;

    @PrePersist
    protected void onCreate() {
        enrollmentDate = LocalDateTime.now();
        if (status == null) {
            status = Enums.EnrollmentStatus.PENDING;
        }
        if (isBlocked == null) {
            isBlocked = false;
        }
    }
}
