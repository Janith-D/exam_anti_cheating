package com.example.anti_cheating_backend.repo;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.anti_cheating_backend.entity.Student;

public interface StudentRepo extends JpaRepository<Student, Long> {
    // Changed to return Optional to handle potential duplicates gracefully
    @Query("SELECT s FROM Student s WHERE s.userName = :userName ORDER BY s.id DESC")
    Optional<Student> findFirstByUserName(@Param("userName") String userName);
    
    // Fallback method for backward compatibility
    default Student findByUserName(String userName) {
        return findFirstByUserName(userName).orElse(null);
    }
    
    @Query("SELECT s FROM Student s WHERE s.email = :email ORDER BY s.id DESC")
    Optional<Student> findFirstByEmail(@Param("email") String email);
    
    default Student findByEmail(String email) {
        return findFirstByEmail(email).orElse(null);
    }
    
    @Query("SELECT s FROM Student s WHERE s.studentId = :studentId ORDER BY s.id DESC")
    Optional<Student> findFirstByStudentId(@Param("studentId") String studentId);
    
    default Student findByStudentId(String studentId) {
        return findFirstByStudentId(studentId).orElse(null);
    }
    
    List<Student> findByIsActiveTrue();
}