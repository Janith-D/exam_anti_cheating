package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.dto.AvailableExamDTO;
import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.service.ExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/exams")
@CrossOrigin(origins = "http://localhost:4200")
public class ExamController {

    @Autowired
    private ExamService examService;

    // Get all exams (Admin only)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Exam>> getAllExams() {
        return ResponseEntity.ok(examService.getAllExams());
    }

    // Get exam by ID
    @GetMapping("/{examId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getExamById(@PathVariable Long examId) {
        try {
            Exam exam = examService.getExamById(examId);
            return ResponseEntity.ok(exam);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get published exams (available for students)
    @GetMapping("/published")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<List<Exam>> getPublishedExams() {
        return ResponseEntity.ok(examService.getPublishedExams());
    }

    // Get ongoing exams
    @GetMapping("/ongoing")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<List<Exam>> getOngoingExams() {
        return ResponseEntity.ok(examService.getOngoingExams());
    }

    // Create exam (Admin only)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createExam(@RequestBody Exam exam) {
        try {
            Exam createdExam = examService.createExam(exam);
            return ResponseEntity.ok(Map.of(
                "message", "Exam created successfully",
                "examId", createdExam.getId(),
                "exam", createdExam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Update exam (Admin only)
    @PutMapping("/{examId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateExam(@PathVariable Long examId, @RequestBody Exam exam) {
        try {
            Exam updatedExam = examService.updateExam(examId, exam);
            return ResponseEntity.ok(Map.of(
                "message", "Exam updated successfully",
                "exam", updatedExam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete exam (Admin only)
    @DeleteMapping("/{examId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteExam(@PathVariable Long examId) {
        try {
            examService.deleteExam(examId);
            return ResponseEntity.ok(Map.of("message", "Exam deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Publish exam (Admin only)
    @PutMapping("/{examId}/publish")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> publishExam(@PathVariable Long examId) {
        try {
            Exam exam = examService.publishExam(examId);
            return ResponseEntity.ok(Map.of(
                "message", "Exam published successfully",
                "exam", exam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Start exam (Admin only)
    @PutMapping("/{examId}/start")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> startExam(@PathVariable Long examId) {
        try {
            Exam exam = examService.startExam(examId);
            return ResponseEntity.ok(Map.of(
                "message", "Exam started successfully",
                "exam", exam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Complete exam (Admin only)
    @PutMapping("/{examId}/complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> completeExam(@PathVariable Long examId) {
        try {
            Exam exam = examService.completeExam(examId);
            return ResponseEntity.ok(Map.of(
                "message", "Exam completed successfully",
                "exam", exam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Archive exam (Admin only)
    @PutMapping("/{examId}/archive")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> archiveExam(@PathVariable Long examId) {
        try {
            Exam exam = examService.archiveExam(examId);
            return ResponseEntity.ok(Map.of(
                "message", "Exam archived successfully",
                "exam", exam
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    // Get available exams for student
    @GetMapping("/student/{studentId}/available")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getAvailableExamsForStudent(@PathVariable Long studentId) {
        try {
            List<AvailableExamDTO> exams = examService.getAvailableExamsForStudent(studentId);
            return ResponseEntity.ok(exams);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
