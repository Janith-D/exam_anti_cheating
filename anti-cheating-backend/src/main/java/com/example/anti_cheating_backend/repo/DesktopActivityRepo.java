package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.DesktopActivity;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.entity.ExamSession;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface DesktopActivityRepo extends JpaRepository<DesktopActivity, Long> {
    List<DesktopActivity> findByStudent(Student student);
    List<DesktopActivity> findByStudentOrderByTimestampDesc(Student student);
    List<DesktopActivity> findByExamSession(ExamSession examSession);
    List<DesktopActivity> findByExamSessionOrderByTimestampDesc(ExamSession examSession);
    List<DesktopActivity> findByStudentAndExamSession(Student student, ExamSession examSession);
    List<DesktopActivity> findByStudentAndExamSessionOrderByTimestampDesc(Student student, ExamSession examSession);
    List<DesktopActivity> findByActivityType(DesktopActivity.ActivityType activityType);
    List<DesktopActivity> findBySeverityLevelGreaterThanEqual(Integer severityLevel);
    List<DesktopActivity> findByTimestampBetween(LocalDateTime start, LocalDateTime end);
}
