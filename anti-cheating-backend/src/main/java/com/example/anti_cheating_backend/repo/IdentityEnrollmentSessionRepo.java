package com.example.anti_cheating_backend.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.IdentityEnrollmentSession;

public interface IdentityEnrollmentSessionRepo extends JpaRepository<IdentityEnrollmentSession, String> {
	List<IdentityEnrollmentSession> findByStateAndExpiresAtBefore(Enums.IdentityEnrollmentState state, LocalDateTime expiresAt);
}
