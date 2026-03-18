package com.example.anti_cheating_backend.repo;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.ExamSession;

public interface ExamSessionRepo extends JpaRepository<ExamSession,Long> {
    List<ExamSession> findByStatus(Enums.SessionStatus status);

    @Modifying
    @Query("""
        UPDATE ExamSession s
        SET s.status = :completedStatus
        WHERE s.status = :activeStatus
          AND s.endTime IS NOT NULL
          AND s.endTime <= :now
        """)
    int completeExpiredActiveSessions(
        @Param("activeStatus") Enums.SessionStatus activeStatus,
        @Param("completedStatus") Enums.SessionStatus completedStatus,
        @Param("now") LocalDateTime now
    );

    @Query("""
        SELECT s FROM ExamSession s
        WHERE s.status = :activeStatus
          AND (s.endTime IS NULL OR s.endTime > :now)
        """)
    List<ExamSession> findCurrentlyActiveSessions(
        @Param("activeStatus") Enums.SessionStatus activeStatus,
        @Param("now") LocalDateTime now
    );
}
