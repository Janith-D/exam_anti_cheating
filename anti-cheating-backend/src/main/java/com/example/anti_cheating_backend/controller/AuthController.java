package com.example.anti_cheating_backend.controller;

import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.anti_cheating_backend.service.AuthService;

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
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "audio", required = false) List<MultipartFile> audioFiles) {
        try {
            // Validate image
            if (image.isEmpty() || !image.getContentType().startsWith("image/")) {
                throw new RuntimeException("Invalid or missing image file");
            }
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());

            // Convert audio Blobs to base64 data URLs
            List<String> audioBase64List = null;
            if (audioFiles != null && !audioFiles.isEmpty()) {
                audioBase64List = new ArrayList<>();
                for (MultipartFile audioFile : audioFiles) {
                    if (!audioFile.isEmpty()) {
                        String mime = audioFile.getContentType() != null ? audioFile.getContentType() : "audio/webm";
                        String encoded = Base64.getEncoder().encodeToString(audioFile.getBytes());
                        audioBase64List.add("data:" + mime + ";base64," + encoded);
                    }
                }
            }

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
            if (audioBase64List != null && !audioBase64List.isEmpty()) {
                payload.put("audio", audioBase64List);
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
            @RequestParam(value = "audio", required = false) MultipartFile audio,
            @RequestParam("image") MultipartFile image) {
        try {
            // Convert image MultipartFile to base64
            String imageBase64 = "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(image.getBytes());

            // Convert audio MultipartFile to base64 data URL (avoids URL-encoding corruption)
            String audioBase64 = null;
            if (audio != null && !audio.isEmpty()) {
                String mime = audio.getContentType() != null ? audio.getContentType() : "audio/webm";
                audioBase64 = "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(audio.getBytes());
            }

            // Prepare payload for AuthService
            Map<String, Object> payload = new HashMap<>();
            payload.put("userName", userName);
            payload.put("password", password);
            payload.put("image", imageBase64);
            if (audioBase64 != null) {
                payload.put("audio", audioBase64);
            }

            Map<String, Object> response = authService.login(payload);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }

    // Simple login without face verification (for first-time login or when face not enrolled)
    @PostMapping(value = "/simple-login", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, Object>> simpleLogin(@RequestBody Map<String, String> credentials) {
        try {
            String userName = credentials.get("userName");
            String password = credentials.get("password");

            Map<String, Object> response = authService.simpleLogin(userName, password);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("error", e.getMessage());
            return ResponseEntity.badRequest().body(errorResponse);
        }
    }
}