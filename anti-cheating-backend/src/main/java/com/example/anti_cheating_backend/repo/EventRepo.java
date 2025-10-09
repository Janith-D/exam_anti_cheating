package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Enums;
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

    // Single type count
    @Query("SELECT COUNT(e) FROM Event e WHERE e.student.id = :studentId AND e.type = :type AND e.timestamp BETWEEN :start AND :end")
    Long countByStudentIdAndTypeAndTimestampBetween(@Param("studentId") Long studentId,
                                                    @Param("type") Enums.EventType type,
                                                    @Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);

    // Multiple types count (for array like COPY/PASTE)
    // Multiple types count (for array like COPY/PASTE)
    @Query("SELECT COUNT(e) FROM Event e WHERE e.student.id = :studentId AND e.type IN :types AND e.timestamp BETWEEN :start AND :end")
    Long countByStudentIdAndTypesAndTimestampBetweens(@Param("studentId") Long studentId,
                                                     @Param("types") List<Enums.EventType> types,  // Fixed: Param name matches
                                                     @Param("start") LocalDateTime start,
                                                     @Param("end") LocalDateTime end);

    // Additional methods (e.g., for blur duration - sum custom field if you add one)
    @Query("SELECT SUM(e.duration) FROM Event e WHERE e.student.id = :studentId AND e.type = :type AND e.timestamp BETWEEN :start AND :end")
    Long sumDurationByStudentIdAndTypeAndTimestampBetween(@Param("studentId") Long studentId,
                                                          @Param("type") Enums.EventType type,
                                                          @Param("start") LocalDateTime start,
                                                          @Param("end") LocalDateTime end);
}
