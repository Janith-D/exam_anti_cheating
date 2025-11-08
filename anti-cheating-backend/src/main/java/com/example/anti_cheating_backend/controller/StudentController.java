package com.example.anti_cheating_backend.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.service.AlertService;
import com.example.anti_cheating_backend.service.EnrollmentService;
import com.example.anti_cheating_backend.service.StudentService;

@RestController
@RequestMapping("api/students")
public class StudentController {

    private static final Logger LOGGER = Logger.getLogger(StudentController.class.getName());

    @Autowired
    private StudentService studentService;

    @Autowired
    private AlertService alertService;

    @Autowired
    private EnrollmentService enrollmentService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #username")
    public ResponseEntity<?> getStudentById(@PathVariable Long id) {
        try {
            Optional<Student> student = studentService.findById(id);
            if (student.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
            }
            return ResponseEntity.ok(student.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #username")
    public ResponseEntity<?> getStudentByUsername(@PathVariable String username) {
        try {
            Student student = studentService.findByUsername(username);
            if (student == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
            }
            return ResponseEntity.ok(student);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Student>> getAllActiveStudents() {
        return ResponseEntity.ok(studentService.getAllActiveStudents());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #studentDetails.userName")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Student studentDetails) {
        try {
            Student updatedStudent = studentService.updateStudent(id, studentDetails);
            return ResponseEntity.ok(Map.of("message", "Student updated", "studentId", updatedStudent.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get student alerts
     */
    @GetMapping("/{id}/alerts")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudentAlerts(@PathVariable Long id) {
        try {
            List<Alert> alerts = alertService.getAlertsByStudent(id);
            return ResponseEntity.ok(alerts);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get student enrollments
     */
    @GetMapping("/{id}/enrollments")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudentEnrollments(@PathVariable Long id) {
        try {
            List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(id);
            return ResponseEntity.ok(enrollments);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get comprehensive student statistics
     */
    @GetMapping("/{id}/statistics")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getStudentStatistics(@PathVariable Long id) {
        try {
            Optional<Student> studentOpt = studentService.findById(id);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
            }

            Student student = studentOpt.get();
            List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(id);
            List<Alert> alerts = alertService.getAlertsByStudent(id);

            // Calculate statistics
            Map<String, Object> statistics = new HashMap<>();
            statistics.put("studentId", student.getId());
            statistics.put("studentName", student.getUserName());
            statistics.put("totalExams", enrollments.size());

            // Count verified vs pending enrollments (completed vs active exams)
            long completedExams = enrollments.stream()
                    .filter(e -> e.getStatus() == Enums.EnrollmentStatus.APPROVED || 
                                 e.getStatus() == Enums.EnrollmentStatus.VERIFIED)
                    .count();
            long activeExams = enrollments.stream()
                    .filter(e -> e.getStatus() == Enums.EnrollmentStatus.PENDING)
                    .count();
            
            statistics.put("completedExams", completedExams);
            statistics.put("activeExams", activeExams);

            // Alert statistics
            statistics.put("totalAlerts", alerts.size());
            statistics.put("criticalAlerts", alerts.stream()
                    .filter(a -> a.getSeverity() == Enums.AlertSeverity.CRITICAL).count());
            statistics.put("highAlerts", alerts.stream()
                    .filter(a -> a.getSeverity() == Enums.AlertSeverity.HIGH).count());
            statistics.put("mediumAlerts", alerts.stream()
                    .filter(a -> a.getSeverity() == Enums.AlertSeverity.MEDIUM).count());
            statistics.put("lowAlerts", alerts.stream()
                    .filter(a -> a.getSeverity() == Enums.AlertSeverity.LOW).count());

            // Block status
            boolean currentlyBlocked = enrollments.stream()
                    .anyMatch(e -> Boolean.TRUE.equals(e.getIsBlocked()));
            statistics.put("currentlyBlocked", currentlyBlocked);

            // Block history
            List<Map<String, Object>> blockHistory = enrollments.stream()
                    .filter(e -> e.getBlockedAt() != null)
                    .map(e -> {
                        Map<String, Object> blockInfo = new HashMap<>();
                        blockInfo.put("examId", e.getExam() != null 
                                ? e.getExam().getId() : null);
                        blockInfo.put("examTitle", e.getExam() != null 
                                ? e.getExam().getTitle() : "Unknown");
                        blockInfo.put("blockedAt", e.getBlockedAt());
                        blockInfo.put("blockedBy", e.getBlockedBy());
                        blockInfo.put("blockReason", e.getBlockReason());
                        blockInfo.put("unblockedAt", e.getUnblockedAt());
                        blockInfo.put("unblockedBy", e.getUnblockedBy());
                        return blockInfo;
                    })
                    .toList();
            statistics.put("blockHistory", blockHistory);

            // Last activity (most recent alert or enrollment update)
            if (!alerts.isEmpty()) {
                statistics.put("lastActive", alerts.get(0).getTimestamp());
            } else if (!enrollments.isEmpty()) {
                statistics.put("lastActive", enrollments.get(0).getEnrollmentDate());
            }

            return ResponseEntity.ok(statistics);
        } catch (RuntimeException e) {
            LOGGER.severe(() -> "Error calculating student statistics: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
