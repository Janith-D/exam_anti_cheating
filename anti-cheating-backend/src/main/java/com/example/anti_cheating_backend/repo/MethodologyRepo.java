package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Methodology;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MethodologyRepo extends JpaRepository<Methodology, Long> {
    List<Methodology> findByStatus(Enums.MethodologyStatus status);
}
