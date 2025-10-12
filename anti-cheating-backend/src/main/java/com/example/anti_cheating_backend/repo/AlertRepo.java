package com.example.anti_cheating_backend.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enums;

public interface AlertRepo extends JpaRepository<Alert, Long> {
    List<Alert> findByStatusAndTimestampAfter(Enums.AlertStatus status, LocalDateTime timestamp);

    List<Alert> findByStudentId(Long studentId);
    
    List<Alert> findBySeverity(Enums.AlertSeverity severity);

}
