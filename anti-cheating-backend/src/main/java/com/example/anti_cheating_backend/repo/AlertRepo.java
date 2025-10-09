package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Alert;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface AlertRepo extends JpaRepository<Alert, Long> {
    List<Alert> findByStatusAndTimestampAfter(String status, LocalDateTime timestamp);

    List<Alert> findByStudentId(Long studentId);
    List<Alert> findBySeverity(String severity);

}
