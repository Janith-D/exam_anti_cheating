package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.service.AlertService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;

    // Get all alerts (including resolved and unresolved)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> getAllAlerts() {
        return ResponseEntity.ok(alertService.getAllAlerts());
    }

    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> getActiveAlerts(){
        return ResponseEntity.ok(alertService.getActiveAlerts());
    }
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> getAlertsByStudent(@PathVariable Long studentId) {
        return ResponseEntity.ok(alertService.getAlertsByStudent(studentId));
    }

    @GetMapping("/severity/{severity}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Alert>> getAlertsBySeverity(@PathVariable String severity) {
        try {
            Enums.AlertSeverity alertSeverity = Enums.AlertSeverity.valueOf(severity.toUpperCase());
            return ResponseEntity.ok(alertService.getAlertsBySeverity(alertSeverity));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PutMapping("/resolve/{alertId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> resolveAlert(@PathVariable Long alertId, @RequestBody Map<String, String> payload) {
        try {
            String resolvedBy = payload.get("resolvedBy");
            Alert resolvedAlert = alertService.resolveAlert(alertId, resolvedBy);
            return ResponseEntity.ok(Map.of("message", "Alert resolved", "alertId", resolvedAlert.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
