
package com.example.anti_cheating_backend.controller;

import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.anti_cheating_backend.dto.EnrollmentDTO;
import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.service.EnrollmentService;

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
            
            System.out.println("Found " + enrollments.size() + " enrollments for student " + studentId);

            // Safely filter and convert enrollments
            List<EnrollmentDTO> enrollmentDTOs = enrollments.stream()
                    .filter(e -> {
                        try {
                            // Only include enrollments that have an exam
                            return e.getExam() != null && e.getStudent() != null;
                        } catch (Exception ex) {
                            // Skip enrollments with invalid exam references
                            System.err.println("Skipping enrollment " + e.getId() + " due to error: " + ex.getMessage());
                            return false;
                        }
                    })
                    .map(e -> {
                        try {
                            return convertToDTO(e);
                        } catch (Exception ex) {
                            System.err.println("Error converting enrollment " + e.getId() + ": " + ex.getMessage());
                            return null;
                        }
                    })
                    .filter(dto -> dto != null)
                    .collect(Collectors.toList());
            
            System.out.println("Returning " + enrollmentDTOs.size() + " valid enrollments");
            return ResponseEntity.ok(enrollmentDTOs);
        } catch (Exception e) {
            System.err.println("Error in getStudentEnrollments: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/exam/{examId}/students")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getExamEnrollments(@PathVariable Long examId) {
        try {
            List<Enrollment> enrollments = enrollmentService.getExamEnrollments(examId);
            List<EnrollmentDTO> enrollmentDTOs = enrollments.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(enrollmentDTOs);
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
            List<EnrollmentDTO> enrollmentDTOs = enrollments.stream()
                    .map(this::convertToDTO)
                    .collect(Collectors.toList());
            return ResponseEntity.ok(enrollmentDTOs);
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

    // Helper method to convert Enrollment entity to DTO
    private EnrollmentDTO convertToDTO(Enrollment enrollment) {
        if (enrollment == null) {
            throw new IllegalArgumentException("Enrollment cannot be null");
        }

        EnrollmentDTO dto = new EnrollmentDTO();
        dto.setId(enrollment.getId());

        // Handle Student with try-catch
        try {
            if (enrollment.getStudent() != null) {
                dto.setStudentId(enrollment.getStudent().getId());
                dto.setStudentName(enrollment.getStudent().getUserName());
            }
        } catch (Exception e) {
            System.err.println("Error accessing student for enrollment " + enrollment.getId() + ": " + e.getMessage());
        }

        // Handle Exam with try-catch
        try {
            if (enrollment.getExam() != null) {
                dto.setExamId(enrollment.getExam().getId());
                dto.setExamTitle(enrollment.getExam().getTitle());
                dto.setExamDescription(enrollment.getExam().getDescription());
                dto.setExamStartDate(enrollment.getExam().getStartDate());
                dto.setExamEndDate(enrollment.getExam().getEndDate());
            }
        } catch (Exception e) {
            System.err.println("Error accessing exam for enrollment " + enrollment.getId() + ": " + e.getMessage());
        }

        dto.setStatus(enrollment.getStatus());
        dto.setIsVerified(enrollment.getIsVerified());
        dto.setVerificationScore(enrollment.getVerificationScore());
        dto.setEnrollmentDate(enrollment.getEnrollmentDate());
        dto.setLastVerification(enrollment.getLastVerification());
        return dto;
    }
}
