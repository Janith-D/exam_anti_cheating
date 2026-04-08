package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "biometric_profile")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class BiometricProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "student_id", nullable = false, unique = true)
    private Long studentId;

    @Column(name = "face_template", columnDefinition = "TEXT")
    private String faceTemplate;

    @Column(name = "voice_template", columnDefinition = "TEXT")
    private String voiceTemplate;

    @Column(name = "behavior_template", columnDefinition = "TEXT")
    private String behaviorTemplate;

    @Column(name = "quality_meta", columnDefinition = "TEXT")
    private String qualityMeta;

    @Column(name = "model_versions", columnDefinition = "TEXT")
    private String modelVersions;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
