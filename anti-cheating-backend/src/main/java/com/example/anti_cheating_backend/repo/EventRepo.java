package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.entity.Student;
import jdk.jfr.EventType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepo extends JpaRepository<Event,Long> {
    List<Event> findByUserIdAAndTimestampBetween (Student studentId, LocalDateTime start, LocalDateTime end);

    @Query("SELECT COUNT(e) FROM Event e WHERE e.student.id = :studentId AND e.type IN :types AND e.timestamp BETWEEN :start AND :end")
    Long countByStudentIdAndTypeAndTimestampBetween(
            @Param("studentId") Long studentId,
            @Param("types") EventType type,
            @Param("start") LocalDateTime start,
            @Param("end") LocalDateTime end);
}
