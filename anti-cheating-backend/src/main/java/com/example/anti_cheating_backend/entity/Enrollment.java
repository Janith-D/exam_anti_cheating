package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "enrollments")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Enrollment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;
    @Column(name = "face_embedding",columnDefinition = "TEXT")
    private String faceEmbedding;
    @Column(name = "enrollment_images",columnDefinition = "TEXT")
    private String enrollmentImages;
    @Column(name = "verification_score")
    private Double verificationScore;
    @Column(name = "is_verified")
    private Boolean isVerified = false;
    @Column(name = "enrollment_date")
    private LocalDateTime enrollmentDate;
    @Column(name = "last_verification")
    private LocalDateTime lastVerification;

    @PrePersist
    protected void onCreate(){
        enrollmentDate = LocalDateTime.now();
    }
}
