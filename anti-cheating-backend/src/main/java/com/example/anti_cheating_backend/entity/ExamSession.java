package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Table(name = "exam_sessions")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExamSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(name = "exam_name")
    private String examName;
    @Column(name = "start_time")
    private LocalDateTime startTime;
    @Column(name = "end_time")
    private LocalDateTime endTime;
    @Column(name = "duration_minutes")
    private Integer durationMinutes;
    @Enumerated(EnumType.STRING)
    private Enums.SessionStatus status = Enums.SessionStatus.SCHEDULED;
    @Column(name = "created_by")
    private String createdBy;
    @OneToMany(mappedBy = "examSession",cascade = CascadeType.ALL)
    private List<Event> events;
}
