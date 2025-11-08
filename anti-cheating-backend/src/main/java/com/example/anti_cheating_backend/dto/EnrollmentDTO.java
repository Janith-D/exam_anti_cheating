package com.example.anti_cheating_backend.dto;

import com.example.anti_cheating_backend.entity.Enums;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class EnrollmentDTO {
    private Long id;
    private Long studentId;
    private String studentName;
    private Long examId;
    private String examTitle;
    private String examDescription;
    private LocalDateTime examStartDate;
    private LocalDateTime examEndDate;
    private Enums.EnrollmentStatus status;
    private Boolean isVerified;
    private Double verificationScore;
    private LocalDateTime enrollmentDate;
    private LocalDateTime lastVerification;
    private Boolean isBlocked;
    private LocalDateTime blockedAt;
    private String blockedBy;
    private String blockReason;
}
