package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.DesktopActivity;
import com.example.anti_cheating_backend.entity.Screenshot;
import com.example.anti_cheating_backend.security.JwtUtil;
import com.example.anti_cheating_backend.service.DesktopActivityService;
import com.example.anti_cheating_backend.service.ScreenshotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@RestController
@RequestMapping("/api/desktop-monitor")
@CrossOrigin(origins = "*")
public class DesktopMonitorController {

    private static final Logger LOGGER = Logger.getLogger(DesktopMonitorController.class.getName());

    @Autowired
    private ScreenshotService screenshotService;
    
    @Autowired
    private DesktopActivityService desktopActivityService;
    
    @Autowired
    private JwtUtil jwtUtil;

    /**
     * Authenticate desktop application with JWT token
     */
    @PostMapping("/authenticate")
    public ResponseEntity<?> authenticate(@RequestBody Map<String, String> request) {
        try {
            String token = request.get("token");
            if (token == null || token.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Token is required"));
            }
            
            // Validate JWT token
            String username = jwtUtil.extractUsername(token);
            if (username == null || !jwtUtil.validateToken(token, username)) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Invalid or expired token"));
            }
            
            LOGGER.info("Desktop app authenticated for user: " + username);
            
            return ResponseEntity.ok(Map.of(
                "message", "Authentication successful",
                "username", username,
                "authenticated", true
            ));
            
        } catch (Exception e) {
            LOGGER.severe("Authentication error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Authentication failed: " + e.getMessage()));
        }
    }

    /**
     * Upload screenshot from desktop application
     */
    @PostMapping(value = "/screenshot", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> uploadScreenshot(
            @RequestParam("studentId") Long studentId,
            @RequestParam(value = "examSessionId", required = false) Long examSessionId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "activeWindow", required = false) String activeWindow,
            @RequestParam(value = "runningProcesses", required = false) String runningProcesses,
            @RequestParam(value = "captureSource", required = false) String captureSource) {
        
        try {
            LOGGER.info(String.format("Received screenshot upload - studentId: %d, examSessionId: %s, activeWindow: %s", 
                studentId, examSessionId, activeWindow));
            
            if (file.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "File is empty"));
            }
            
            Screenshot screenshot = screenshotService.saveScreenshot(
                studentId, 
                examSessionId, 
                file, 
                activeWindow, 
                runningProcesses, 
                captureSource
            );
            
            LOGGER.info(String.format("Screenshot saved successfully: %d", screenshot.getId()));
            
            Map<String, Object> response = new HashMap<>();
            response.put("message", "Screenshot uploaded successfully");
            response.put("screenshotId", screenshot.getId());
            response.put("flagged", screenshot.getFlaggedSuspicious());
            if (screenshot.getFlaggedSuspicious()) {
                response.put("reason", screenshot.getSuspiciousReason());
            }
            
            return ResponseEntity.ok(response);
            
        } catch (Exception e) {
            LOGGER.severe("Error uploading screenshot: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to upload screenshot: " + e.getMessage()));
        }
    }

    /**
     * Log desktop activity
     */
    @PostMapping("/activity")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> logActivity(@RequestBody Map<String, Object> request) {
        try {
            Long studentId = ((Number) request.get("studentId")).longValue();
            Long examSessionId = request.containsKey("examSessionId") && request.get("examSessionId") != null
                ? ((Number) request.get("examSessionId")).longValue() 
                : null;
            
            String activityTypeStr = (String) request.get("activityType");
            DesktopActivity.ActivityType activityType = DesktopActivity.ActivityType.valueOf(activityTypeStr);
            
            String details = (String) request.get("details");
            String activeWindow = (String) request.get("activeWindow");
            String applicationName = (String) request.get("applicationName");
            Integer severityLevel = request.containsKey("severityLevel") 
                ? ((Number) request.get("severityLevel")).intValue() 
                : 1;
            
            LOGGER.info(String.format("Logging desktop activity - studentId: %d, type: %s, severity: %d", 
                studentId, activityType, severityLevel));
            
            DesktopActivity activity = desktopActivityService.logActivity(
                studentId, 
                examSessionId, 
                activityType, 
                details, 
                activeWindow, 
                applicationName, 
                severityLevel
            );
            
            return ResponseEntity.ok(Map.of(
                "message", "Activity logged successfully",
                "activityId", activity.getId()
            ));
            
        } catch (IllegalArgumentException e) {
            LOGGER.warning("Invalid activity type: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Invalid activity type. Valid types: " + 
                         java.util.Arrays.toString(DesktopActivity.ActivityType.values())
            ));
        } catch (Exception e) {
            LOGGER.severe("Error logging activity: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to log activity: " + e.getMessage()));
        }
    }

    /**
     * Get screenshots for a student
     */
    @GetMapping("/screenshots/student/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getScreenshotsByStudent(@PathVariable Long studentId) {
        try {
            List<Screenshot> screenshots = screenshotService.getScreenshotsByStudent(studentId);
            return ResponseEntity.ok(screenshots);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get screenshots for an exam session
     */
    @GetMapping("/screenshots/session/{examSessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getScreenshotsBySession(@PathVariable Long examSessionId) {
        try {
            List<Screenshot> screenshots = screenshotService.getScreenshotsByExamSession(examSessionId);
            return ResponseEntity.ok(screenshots);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get screenshots for a student in a specific exam session
     */
    @GetMapping("/screenshots/student/{studentId}/session/{examSessionId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getScreenshotsByStudentAndSession(
            @PathVariable Long studentId, 
            @PathVariable Long examSessionId) {
        try {
            List<Screenshot> screenshots = screenshotService.getScreenshotsByStudentAndSession(
                studentId, examSessionId);
            return ResponseEntity.ok(screenshots);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Download screenshot file
     */
    @GetMapping("/screenshots/{screenshotId}/download")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<Resource> downloadScreenshot(@PathVariable Long screenshotId) {
        try {
            byte[] fileData = screenshotService.getScreenshotFile(screenshotId);
            ByteArrayResource resource = new ByteArrayResource(fileData);
            
            return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=screenshot_" + screenshotId + ".png")
                .contentType(MediaType.IMAGE_PNG)
                .contentLength(fileData.length)
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    /**
     * Get flagged screenshots
     */
    @GetMapping("/screenshots/flagged")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getFlaggedScreenshots() {
        try {
            List<Screenshot> screenshots = screenshotService.getFlaggedScreenshots();
            return ResponseEntity.ok(screenshots);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get desktop activities for a student
     */
    @GetMapping("/activities/student/{studentId}")
    @PreAuthorize("hasAnyRole('STUDENT', 'ADMIN')")
    public ResponseEntity<?> getActivitiesByStudent(@PathVariable Long studentId) {
        try {
            List<DesktopActivity> activities = desktopActivityService.getActivitiesByStudent(studentId);
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get desktop activities for an exam session
     */
    @GetMapping("/activities/session/{examSessionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getActivitiesBySession(@PathVariable Long examSessionId) {
        try {
            List<DesktopActivity> activities = desktopActivityService.getActivitiesByExamSession(examSessionId);
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Get high-severity activities
     */
    @GetMapping("/activities/high-severity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getHighSeverityActivities() {
        try {
            List<DesktopActivity> activities = desktopActivityService.getHighSeverityActivities();
            return ResponseEntity.ok(activities);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/status")
    public ResponseEntity<?> getStatus() {
        return ResponseEntity.ok(Map.of(
            "status", "online",
            "message", "Desktop monitoring API is running",
            "timestamp", System.currentTimeMillis()
        ));
    }
}
