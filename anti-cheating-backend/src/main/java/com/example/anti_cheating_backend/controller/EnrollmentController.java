package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.service.EnrollmentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/enrollment")
public class EnrollmentController {

    @Autowired
    private EnrollmentService enrollmentService;

    @PostMapping("/enroll")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> enroll(@RequestBody Map<String,Object> payload){
        try{
            Long studentId = Long.parseLong(payload.get("studentId").toString());
            String imageBase64 = (String) payload.get("image");
            Enrollment enrollment = enrollmentService.enroll(studentId,imageBase64);
            return ResponseEntity.ok(Map.of("message","Enrollment successful","enrollment",enrollment.getId()));
        } catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
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
