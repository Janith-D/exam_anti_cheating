package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EnrollmentRepo extends JpaRepository<Enrollment,Long> {
    // Legacy method for backward compatibility
    Enrollment findByStudentId(Long studentId);
    
    // Exam-based enrollment queries
    Optional<Enrollment> findByStudentIdAndExamId(Long studentId, Long examId);
    List<Enrollment> findAllByStudentId(Long studentId);
    List<Enrollment> findByExamId(Long examId);
    List<Enrollment> findByExamIdAndStatus(Long examId, Enums.EnrollmentStatus status);
    boolean existsByStudentIdAndExamId(Long studentId, Long examId);
    long countByExamIdAndStatus(Long examId, Enums.EnrollmentStatus status);
}
