package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExamSessionRepo extends JpaRepository<ExamSession,Long> {
    List<ExamSession> findByStatus(Enums.SessionStatus status);
}
