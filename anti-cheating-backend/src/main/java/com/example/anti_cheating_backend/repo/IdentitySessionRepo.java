package com.example.anti_cheating_backend.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.IdentitySession;

public interface IdentitySessionRepo extends JpaRepository<IdentitySession, String> {
}
