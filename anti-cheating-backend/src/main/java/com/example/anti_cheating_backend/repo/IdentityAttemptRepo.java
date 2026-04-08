package com.example.anti_cheating_backend.repo;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.IdentityAttempt;

public interface IdentityAttemptRepo extends JpaRepository<IdentityAttempt, String> {
    Optional<IdentityAttempt> findTopBySessionSessionIdOrderByCreatedAtDesc(String sessionId);
}
