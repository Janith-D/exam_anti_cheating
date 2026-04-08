package com.example.anti_cheating_backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.BiometricProfile;

public interface BiometricProfileRepo extends JpaRepository<BiometricProfile, Long> {
    Optional<BiometricProfile> findByStudentId(Long studentId);
}
