package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Base64;
import java.util.HashMap;
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
}
