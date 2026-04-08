package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "identity_enrollment_session")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityEnrollmentSession {

    @Id
    @Column(name = "enrollment_token", nullable = false, length = 64)
    private String enrollmentToken;

    @Column(name = "student_id", nullable = false)
    private Long studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private Enums.IdentityEnrollmentState state;

    @Column(name = "face_template", columnDefinition = "TEXT")
    private String faceTemplate;

    @Column(name = "voice_template", columnDefinition = "TEXT")
    private String voiceTemplate;

    @Column(name = "behavior_template", columnDefinition = "TEXT")
    private String behaviorTemplate;

    @Column(name = "quality_meta_json", columnDefinition = "TEXT")
    private String qualityMetaJson;

    @Column(name = "model_versions_json", columnDefinition = "TEXT")
    private String modelVersionsJson;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        if (startedAt == null) {
            startedAt = LocalDateTime.now();
        }
        if (state == null) {
            state = Enums.IdentityEnrollmentState.ACTIVE;
        }
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }
}
