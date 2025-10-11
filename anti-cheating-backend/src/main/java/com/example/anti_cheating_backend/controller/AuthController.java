package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.service.AuthService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private AuthService authService;

    @PostMapping(value = "/register", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> register(
            @RequestParam("userName") String userName,
            @RequestParam("password") String password,
            @RequestParam("image") MultipartFile image,
            @RequestParam("role") String role,
            @RequestParam("firstName") String firstName,
            @RequestParam("lastName") String lastName,
            @RequestParam("email") String email,
            @RequestParam(value = "studentId", required = false) String studentId) {
        try {
            // Validate image
            if (image.isEmpty() || !image.getContentType().startsWith("image/")) {
                throw new RuntimeException("Invalid or missing image file");
            }
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());

            // Prepare payload for AuthService
            Map<String, Object> payload = new HashMap<>();
            payload.put("userName", userName);
            payload.put("password", password);
            payload.put("image", imageBase64);
            payload.put("role", role);
            payload.put("firstName", firstName);
            payload.put("lastName", lastName);
            payload.put("email", email);
            if (studentId != null) {
                payload.put("studentId", studentId);
            }

            Map<String, Object> response = authService.register(payload);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", "Registration failed: " + e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    @PostMapping(value = "/login", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> login(
            @RequestParam("userName") String userName,
            @RequestParam("password") String password,
            @RequestParam("image") MultipartFile image) {
        try {
            // Convert MultipartFile to base64
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());

            // Prepare payload for AuthService
            Map<String, Object> payload = new HashMap<>();
            payload.put("userName", userName);
            payload.put("password", password);
            payload.put("image", imageBase64);

            Map<String, Object> response = authService.login(payload);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}