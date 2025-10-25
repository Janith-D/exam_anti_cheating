package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/enrollment")
public class EnrollmentController {

    @Autowired
    private EnrollmentService enrollmentService;

    @PostMapping(value = "/enroll", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> enroll(
            @RequestParam("studentId") Long studentId,
            @RequestParam("image") MultipartFile image,
            @RequestHeader("Authorization") String authorization) {
        try {
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());
            Enrollment enrollment = enrollmentService.enroll(studentId, imageBase64); // Assumes EnrollmentService method
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Enrolled successfully");
            response.put("enrollmentId", enrollment.getId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/{studentId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<?> getEnrollment(@PathVariable Long studentId){
        try {
            Enrollment enrollment = enrollmentService.getEnrollment(studentId);
            if (enrollment== null){
                return ResponseEntity.status(404).body(Map.of("error","No enrollment found"));
            }
            return ResponseEntity.ok(enrollment);
        } catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }

    // New exam-based enrollment endpoints
    @PostMapping(value = "/exam/{examId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<Map<String, Object>> enrollInExam(
            @PathVariable Long examId,
            @RequestParam("studentId") Long studentId,
            @RequestParam("image") MultipartFile image) {
        try {
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());
            Enrollment enrollment = enrollmentService.enrollInExam(studentId, examId, imageBase64);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Enrolled in exam successfully");
            response.put("enrollmentId", enrollment.getId());
            response.put("examId", examId);
            response.put("status", enrollment.getStatus());
            response.put("verificationScore", enrollment.getVerificationScore());
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @GetMapping("/student/{studentId}/exams")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<?> getStudentEnrollments(@PathVariable Long studentId) {
        try {
            List<Enrollment> enrollments = enrollmentService.getStudentEnrollments(studentId);
            return ResponseEntity.ok(enrollments);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exam/{examId}/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getExamEnrollments(@PathVariable Long examId) {
        try {
            List<Enrollment> enrollments = enrollmentService.getExamEnrollments(examId);
            return ResponseEntity.ok(enrollments);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exam/{examId}/students/status/{status}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getExamEnrollmentsByStatus(
            @PathVariable Long examId,
            @PathVariable String status) {
        try {
            Enums.EnrollmentStatus enrollmentStatus = Enums.EnrollmentStatus.valueOf(status.toUpperCase());
            List<Enrollment> enrollments = enrollmentService.getExamEnrollmentsByStatus(examId, enrollmentStatus);
            return ResponseEntity.ok(enrollments);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PutMapping("/{enrollmentId}/status")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateEnrollmentStatus(
            @PathVariable Long enrollmentId,
            @RequestParam String status) {
        try {
            Enums.EnrollmentStatus enrollmentStatus = Enums.EnrollmentStatus.valueOf(status.toUpperCase());
            Enrollment enrollment = enrollmentService.updateEnrollmentStatus(enrollmentId, enrollmentStatus);
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Enrollment status updated successfully");
            response.put("enrollmentId", enrollment.getId());
            response.put("status", enrollment.getStatus());
            
            return ResponseEntity.ok(response);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid status: " + status));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/check/{studentId}/exam/{examId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<?> checkEnrollment(
            @PathVariable Long studentId,
            @PathVariable Long examId) {
        try {
            boolean isEnrolled = enrollmentService.isStudentEnrolledInExam(studentId, examId);
            return ResponseEntity.ok(Map.of("isEnrolled", isEnrolled));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
