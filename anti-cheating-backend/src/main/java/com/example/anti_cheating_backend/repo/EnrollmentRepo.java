package com.example.anti_cheating_backend.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;

public interface EnrollmentRepo extends JpaRepository<Enrollment,Long> {
    // Legacy method for backward compatibility
    Enrollment findByStudentId(Long studentId);
    
    // Get enrollments for a student - simple query without JOIN FETCH to avoid issues with NULL/invalid exam_id
    // This allows the controller to handle lazy loading safely with try-catch
    @Query("SELECT e FROM Enrollment e WHERE e.student.id = :studentId")
    List<Enrollment> findAllByStudentId(@Param("studentId") Long studentId);
    
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.student JOIN FETCH e.exam WHERE e.exam.id = :examId")
    List<Enrollment> findByExamId(@Param("examId") Long examId);
    
    @Query("SELECT e FROM Enrollment e JOIN FETCH e.student JOIN FETCH e.exam WHERE e.exam.id = :examId AND e.status = :status")
    List<Enrollment> findByExamIdAndStatus(@Param("examId") Long examId, @Param("status") Enums.EnrollmentStatus status);
    
    Optional<Enrollment> findByStudentIdAndExamId(Long studentId, Long examId);
    boolean existsByStudentIdAndExamId(Long studentId, Long examId);
    long countByExamIdAndStatus(Long examId, Enums.EnrollmentStatus status);
}
