package com.example.anti_cheating_backend.controller;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping
public class HealthController {

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> response = new HashMap<>();
        response.put("status", "UP");
        response.put("message", "Anti-Cheating Backend is running");
        response.put("timestamp", System.currentTimeMillis());
        return ResponseEntity.ok(response);
    }

    @GetMapping("/api/health-test")
    public ResponseEntity<Map<String, Object>> apiTest() {
        Map<String, Object> response = new HashMap<>();
        response.put("message", "API is accessible");
        response.put("endpoints", Map.of(
                "register", "POST /api/auth/register",
                "login", "POST /api/auth/login",
                "enroll", "POST /api/enrollment/enroll (requires auth)",
                "health", "GET /health"
        ));
        response.put("note", "Most endpoints require JWT authentication. Get token from /api/auth/login");
        return ResponseEntity.ok(response);
    }
}
