package com.example.anti_cheating_backend.controller;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Methodology;
import com.example.anti_cheating_backend.service.MethodologyService;

@RestController
@RequestMapping("/api/methodologies")
@CrossOrigin(origins = "http://localhost:4200")
public class MethodologyController {

    @Autowired
    private MethodologyService methodologyService;

    // Get all methodologies (Admin only)
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Methodology>> getAllMethodologies() {
        return ResponseEntity.ok(methodologyService.getAllMethodologies());
    }

    // Get methodology by ID (Admin only)
    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getMethodologyById(@PathVariable Long id) {
        try {
            Methodology methodology = methodologyService.getMethodologyById(id);
            return ResponseEntity.ok(methodology);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Get active methodologies (Admin only)
    @GetMapping("/active")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Methodology>> getActiveMethodologies() {
        return ResponseEntity.ok(methodologyService.getActiveMethodologies());
    }

    // Create methodology (Admin only)
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> createMethodology(@RequestBody Methodology methodology) {
        try {
            Methodology created = methodologyService.createMethodology(methodology);
            return ResponseEntity.ok(Map.of(
                "message", "Methodology created successfully",
                "methodologyId", created.getId(),
                "methodology", created
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Update methodology (Admin only)
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> updateMethodology(@PathVariable Long id, @RequestBody Methodology methodology) {
        try {
            Methodology updated = methodologyService.updateMethodology(id, methodology);
            return ResponseEntity.ok(Map.of(
                "message", "Methodology updated successfully",
                "methodology", updated
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Delete methodology (Admin only)
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteMethodology(@PathVariable Long id) {
        try {
            methodologyService.deleteMethodology(id);
            return ResponseEntity.ok(Map.of("message", "Methodology deleted successfully"));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Activate methodology (Admin only)
    @PutMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> activateMethodology(@PathVariable Long id) {
        try {
            Methodology methodology = methodologyService.activateMethodology(id);
            return ResponseEntity.ok(Map.of(
                "message", "Methodology activated successfully",
                "methodology", methodology
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // Deactivate methodology (Admin only)
    @PutMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deactivateMethodology(@PathVariable Long id) {
        try {
            Methodology methodology = methodologyService.deactivateMethodology(id);
            return ResponseEntity.ok(Map.of(
                "message", "Methodology deactivated successfully",
                "methodology", methodology
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
