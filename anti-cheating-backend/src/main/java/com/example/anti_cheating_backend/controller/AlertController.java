package com.example.anti_cheating_backend.controller;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.service.AlertService;
import com.example.anti_cheating_backend.service.StudentService;

@RestController
@RequestMapping("/api/alerts")
public class AlertController {

    @Autowired
    private AlertService alertService;

    @Autowired
    private StudentService studentService;

    // Manually create an alert for a student (Admin only)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createAlert(@RequestBody Map<String, Object> payload) {
        try {
            Long studentId = Long.valueOf(payload.get("studentId").toString());
            String severity = payload.get("severity").toString();
            String message = payload.get("message").toString();
            String description = payload.containsKey("description") ? payload.get("description").toString() : "";

            Optional<Student> studentOpt = studentService.findById(studentId);
            if (studentOpt.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Student not found"));
            }
            Enums.AlertSeverity alertSeverity = Enums.AlertSeverity.valueOf(severity.toUpperCase());
            Alert alert = alertService.createAlert(studentOpt.get(), null, message, description, alertSeverity);
            return ResponseEntity.ok(Map.of("message", "Alert created", "alertId", alert.getId()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid severity value"));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

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

    @DeleteMapping("/clear")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> clearAllAlerts() {
        long deleted = alertService.clearAllAlerts();
        return ResponseEntity.ok(Map.of(
            "message", "All alerts cleared successfully",
            "deletedCount", deleted
        ));
    }

    @DeleteMapping("/{alertId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteAlert(@PathVariable Long alertId) {
        alertService.deleteAlert(alertId);
        return ResponseEntity.ok(Map.of("message", "Alert deleted successfully"));
    }

    @PostMapping("/delete-multiple")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMultipleAlerts(@RequestBody Map<String, List<Long>> payload) {
        List<Long> alertIds = payload.get("alertIds");
        if (alertIds == null || alertIds.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "No alert IDs provided"));
        }
        alertService.deleteAlerts(alertIds);
        return ResponseEntity.ok(Map.of("message", alertIds.size() + " alerts deleted successfully"));
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
