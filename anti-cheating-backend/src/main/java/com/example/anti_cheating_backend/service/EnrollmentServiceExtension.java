package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;

/**
 * Extension methods for blocking/unblocking students during exams
 */
@Component
public class EnrollmentServiceExtension {
    
    private static final Logger LOGGER = Logger.getLogger(EnrollmentServiceExtension.class.getName());
    
    @Autowired
    private EnrollmentRepo enrollmentRepo;
    
    // Block student from exam
    public Enrollment blockStudent(Long studentId, Long examId, String adminUsername, String reason) {
        Optional<Enrollment> enrollmentOpt = enrollmentRepo.findByStudentIdAndExamId(studentId, examId);
        if (!enrollmentOpt.isPresent()) {
            throw new RuntimeException("Student enrollment not found for exam");
        }
        
        Enrollment enrollment = enrollmentOpt.get();
        enrollment.setIsBlocked(true);
        enrollment.setBlockedAt(LocalDateTime.now());
        enrollment.setBlockedBy(adminUsername);
        enrollment.setBlockReason(reason != null ? reason : "Repeated cheating violations");
        
        LOGGER.info("Blocking student " + studentId + " from exam " + examId + " by admin: " + adminUsername);
        return enrollmentRepo.save(enrollment);
    }

    // Unblock student from exam
    public Enrollment unblockStudent(Long studentId, Long examId, String adminUsername) {
        Optional<Enrollment> enrollmentOpt = enrollmentRepo.findByStudentIdAndExamId(studentId, examId);
        if (!enrollmentOpt.isPresent()) {
            throw new RuntimeException("Student enrollment not found for exam");
        }
        
        Enrollment enrollment = enrollmentOpt.get();
        enrollment.setIsBlocked(false);
        enrollment.setBlockedAt(null);
        enrollment.setBlockedBy(null);
        enrollment.setBlockReason(null);
        
        LOGGER.info("Unblocking student " + studentId + " from exam " + examId + " by admin: " + adminUsername);
        return enrollmentRepo.save(enrollment);
    }

    // Check if student is blocked in exam
    public boolean isStudentBlocked(Long studentId, Long examId) {
        Optional<Enrollment> enrollmentOpt = enrollmentRepo.findByStudentIdAndExamId(studentId, examId);
        return enrollmentOpt.map(enrollment -> 
            enrollment.getIsBlocked() != null && enrollment.getIsBlocked()
        ).orElse(false);
    }

    // Get block details
    public Map<String, Object> getBlockDetails(Long studentId, Long examId) {
        Optional<Enrollment> enrollmentOpt = enrollmentRepo.findByStudentIdAndExamId(studentId, examId);
        if (!enrollmentOpt.isPresent() || !Boolean.TRUE.equals(enrollmentOpt.get().getIsBlocked())) {
            return Map.of("isBlocked", false);
        }
        
        Enrollment enrollment = enrollmentOpt.get();
        return Map.of(
            "isBlocked", true,
            "blockedAt", enrollment.getBlockedAt(),
            "blockedBy", enrollment.getBlockedBy(),
            "reason", enrollment.getBlockReason() != null ? enrollment.getBlockReason() : "No reason specified"
        );
    }
}
