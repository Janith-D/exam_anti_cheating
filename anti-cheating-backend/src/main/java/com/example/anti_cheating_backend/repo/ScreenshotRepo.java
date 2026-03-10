package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Screenshot;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface ScreenshotRepo extends JpaRepository<Screenshot, Long> {
    List<Screenshot> findByStudent(Student student);
    List<Screenshot> findByStudentOrderByTimestampDesc(Student student);
    List<Screenshot> findByExamSession(ExamSession examSession);
    List<Screenshot> findByExamSessionOrderByTimestampDesc(ExamSession examSession);
    List<Screenshot> findByStudentAndExamSession(Student student, ExamSession examSession);
    List<Screenshot> findByStudentAndExamSessionOrderByTimestampDesc(Student student, ExamSession examSession);
    List<Screenshot> findByFlaggedSuspicious(Boolean flagged);
    List<Screenshot> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
    Long countByStudentAndExamSession(Student student, ExamSession examSession);
}
