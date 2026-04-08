package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Entity
@Table(name = "identity_alert")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class IdentityAlert {

    @Id
    @Column(name = "alert_id", nullable = false, length = 64)
    private String alertId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id", nullable = false)
    @JsonIgnoreProperties({"attempts", "hibernateLazyInitializer", "handler"})
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private IdentitySession session;

    @Enumerated(EnumType.STRING)
    @Column(name = "severity", nullable = false, length = 32)
    private Enums.AlertSeverity severity;

    @Column(name = "category", nullable = false, length = 64)
    private String category;

    @Column(name = "evidence_ref", columnDefinition = "TEXT")
    private String evidenceRef;

    @Column(name = "resolved", nullable = false)
    private Boolean resolved;

    @Column(name = "resolved_by", length = 128)
    private String resolvedBy;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (resolved == null) {
            resolved = false;
        }
    }
}
