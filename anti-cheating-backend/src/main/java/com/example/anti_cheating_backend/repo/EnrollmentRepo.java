package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Enrollment;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EnrollmentRepo extends JpaRepository<Enrollment,Long> {
    Enrollment findByStudentId(Long studentId);
}
