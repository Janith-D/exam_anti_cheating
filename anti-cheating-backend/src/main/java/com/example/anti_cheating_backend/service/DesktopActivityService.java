package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.DesktopActivity;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.DesktopActivityRepo;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class DesktopActivityService {

    @Autowired
    private DesktopActivityRepo desktopActivityRepo;
    
    @Autowired
    private StudentRepo studentRepo;
    
    @Autowired
    private ExamSessionRepo examSessionRepo;
    
    @Autowired
    private AlertService alertService;

    @Transactional
    public DesktopActivity logActivity(Long studentId, Long examSessionId, 
                                       DesktopActivity.ActivityType activityType,
                                       String details, String activeWindow, 
                                       String applicationName, Integer severityLevel) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        
        ExamSession examSession = null;
        if (examSessionId != null) {
            examSession = examSessionRepo.findById(examSessionId)
                    .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        }
        
        DesktopActivity activity = new DesktopActivity();
        activity.setStudent(student);
        activity.setExamSession(examSession);
        activity.setActivityType(activityType);
        activity.setDetails(details);
        activity.setActiveWindow(activeWindow);
        activity.setApplicationName(applicationName);
        activity.setSeverityLevel(severityLevel != null ? severityLevel : 1);
        activity.setTimestamp(LocalDateTime.now());
        
        DesktopActivity saved = desktopActivityRepo.save(activity);
        
        // Create alert for high-severity activities
        if (severityLevel != null && severityLevel >= 4) {
            alertService.createAlert(
                student, 
                examSession, 
                activityType.toString(), 
                details, 
                com.example.anti_cheating_backend.entity.Enums.AlertSeverity.HIGH
            );
        }
        
        return saved;
    }

    public List<DesktopActivity> getActivitiesByStudent(Long studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        return desktopActivityRepo.findByStudentOrderByTimestampDesc(student);
    }

    public List<DesktopActivity> getActivitiesByExamSession(Long examSessionId) {
        ExamSession examSession = examSessionRepo.findById(examSessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        return desktopActivityRepo.findByExamSessionOrderByTimestampDesc(examSession);
    }

    public List<DesktopActivity> getActivitiesByStudentAndSession(Long studentId, Long examSessionId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        ExamSession examSession = examSessionRepo.findById(examSessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        return desktopActivityRepo.findByStudentAndExamSessionOrderByTimestampDesc(student, examSession);
    }

    public List<DesktopActivity> getHighSeverityActivities() {
        return desktopActivityRepo.findBySeverityLevelGreaterThanEqual(4);
    }

    public List<DesktopActivity> getAllActivities() {
        return desktopActivityRepo.findAll();
    }
}
