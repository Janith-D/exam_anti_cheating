package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.entity.Student;
import jdk.jfr.EventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;

public interface EventRepo extends JpaRepository<Event,Long> {
    List<Event> findByUserIdAAndTimestampBetween (Student studentId, LocalDateTime start, LocalDateTime end);
    Long countByStudentIdAndTypeAndTimestampBetween(Long studentId, EventType type, LocalDateTime start, LocalDateTime end);
}
