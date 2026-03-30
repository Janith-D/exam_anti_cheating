package com.example.anti_cheating_backend.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "methodologies")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Methodology {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private Enums.MethodologyType type = Enums.MethodologyType.PROCTORED;

    @Enumerated(EnumType.STRING)
    @Column(name = "monitoring_level")
    private Enums.MonitoringLevel monitoringLevel = Enums.MonitoringLevel.MEDIUM;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private Enums.MethodologyStatus status = Enums.MethodologyStatus.ACTIVE;

    @Column(name = "alert_threshold")
    private Integer alertThreshold = 5;

    @Column(name = "created_by")
    private String createdBy;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
