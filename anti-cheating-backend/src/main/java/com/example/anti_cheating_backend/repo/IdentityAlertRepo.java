package com.example.anti_cheating_backend.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.IdentityAlert;

public interface IdentityAlertRepo extends JpaRepository<IdentityAlert, String> {
    List<IdentityAlert> findBySessionSessionIdOrderByCreatedAtDesc(String sessionId);
}
