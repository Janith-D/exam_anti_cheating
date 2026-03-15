package com.example.anti_cheating_backend.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Screenshot;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;
import com.example.anti_cheating_backend.repo.ScreenshotRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;

@Service
public class ScreenshotService {

    private static final Logger LOGGER = Logger.getLogger(ScreenshotService.class.getName());

    @Autowired
    private ScreenshotRepo screenshotRepo;
    
    @Autowired
    private StudentRepo studentRepo;
    
    @Autowired
    private ExamSessionRepo examSessionRepo;
    
    private static final String UPLOAD_DIR = "uploads/screenshots/";
    private final Path uploadBasePath;
    
    public ScreenshotService() {
        // Resolve upload directory as absolute path from working directory
        this.uploadBasePath = Paths.get(UPLOAD_DIR).toAbsolutePath();
        try {
            Files.createDirectories(this.uploadBasePath);
            System.out.println("Screenshot upload directory: " + this.uploadBasePath);
        } catch (IOException e) {
            System.err.println("Failed to create upload directory: " + e.getMessage());
        }
    }

    @Transactional
    public Screenshot saveScreenshot(Long studentId, Long examSessionId, 
                                     MultipartFile file, String activeWindow,
                                     String runningProcesses, String captureSource) throws IOException {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        
        ExamSession examSession = null;
        if (examSessionId != null && examSessionId > 0) {
            // Look for the exam session, but don't fail if not found
            examSession = examSessionRepo.findById(examSessionId).orElse(null);
            if (examSession == null) {
                LOGGER.warning("Exam session not found: " + examSessionId + ", continuing without session reference");
            }
        }
        
        // Generate unique filename
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"));
        String uniqueId = UUID.randomUUID().toString().substring(0, 8);
        String originalFilename = file.getOriginalFilename();
        String extension = originalFilename != null && originalFilename.contains(".") 
                ? originalFilename.substring(originalFilename.lastIndexOf(".")) 
                : ".png";
        String filename = String.format("screenshot_%d_%s_%s%s", studentId, timestamp, uniqueId, extension);
        
        // Create student-specific subdirectory
        Path studentDir = this.uploadBasePath.resolve("student_" + studentId);
        Files.createDirectories(studentDir);
        
        // Save file using absolute path
        Path filePath = studentDir.resolve(filename);
        file.transferTo(filePath.toFile());
        
        // Create screenshot record
        Screenshot screenshot = new Screenshot();
        screenshot.setStudent(student);
        screenshot.setExamSession(examSession);
        screenshot.setFilePath(filePath.toString());
        screenshot.setActiveWindow(activeWindow);
        screenshot.setRunningProcesses(runningProcesses);
        screenshot.setCaptureSource(captureSource != null ? captureSource : "desktop");
        screenshot.setTimestamp(LocalDateTime.now());
        
        // Auto-flag suspicious screenshots
        if (isSuspicious(activeWindow, runningProcesses)) {
            screenshot.setFlaggedSuspicious(true);
            screenshot.setSuspiciousReason(generateSuspiciousReason(activeWindow, runningProcesses));
        }
        
        return screenshotRepo.save(screenshot);
    }

    public List<Screenshot> getScreenshotsByStudent(Long studentId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        return screenshotRepo.findByStudentOrderByTimestampDesc(student);
    }

    public List<Screenshot> getScreenshotsByExamSession(Long examSessionId) {
        ExamSession examSession = examSessionRepo.findById(examSessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        return screenshotRepo.findByExamSessionOrderByTimestampDesc(examSession);
    }

    public List<Screenshot> getScreenshotsByStudentAndSession(Long studentId, Long examSessionId) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));
        ExamSession examSession = examSessionRepo.findById(examSessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        return screenshotRepo.findByStudentAndExamSessionOrderByTimestampDesc(student, examSession);
    }

    public List<Screenshot> getAllScreenshots() {
        return screenshotRepo.findAll();
    }

    public List<Screenshot> getFlaggedScreenshots() {
        return screenshotRepo.findByFlaggedSuspicious(true);
    }

    public byte[] getScreenshotFile(Long screenshotId) throws IOException {
        Screenshot screenshot = screenshotRepo.findById(screenshotId)
                .orElseThrow(() -> new RuntimeException("Screenshot not found: " + screenshotId));
        Path filePath = Paths.get(screenshot.getFilePath());
        return Files.readAllBytes(filePath);
    }

    @Transactional
    public long clearAllScreenshots() {
        List<Screenshot> screenshots = screenshotRepo.findAll();

        for (Screenshot screenshot : screenshots) {
            try {
                if (screenshot.getFilePath() != null && !screenshot.getFilePath().isBlank()) {
                    Path path = Paths.get(screenshot.getFilePath());
                    Files.deleteIfExists(path);
                }
            } catch (IOException e) {
                LOGGER.warning(() -> String.format("Failed to delete screenshot file: %s - %s", screenshot.getFilePath(), e.getMessage()));
            }
        }

        long total = screenshots.size();
        screenshotRepo.deleteAllInBatch();
        return total;
    }

    private boolean isSuspicious(String activeWindow, String runningProcesses) {
        if (activeWindow == null) {
            return false;
        }

        // Only check the active window (what's actually visible on screen)
        // Background processes should not trigger flags
        String[] suspiciousApps = {
            "TeamViewer", "AnyDesk", "Chrome Remote Desktop",
            "UltraViewer", "RustDesk", "Parsec",
            "ChatGPT", "Bard", "Copilot"
        };

        String windowLower = activeWindow.toLowerCase();

        for (String app : suspiciousApps) {
            if (windowLower.contains(app.toLowerCase())) {
                return true;
            }
        }

        return false;
    }

    private String generateSuspiciousReason(String activeWindow, String runningProcesses) {
        StringBuilder reason = new StringBuilder("Detected suspicious applications: ");
        String[] suspiciousApps = {
            "TeamViewer", "AnyDesk", "Chrome Remote Desktop",
            "UltraViewer", "RustDesk", "Parsec",
            "ChatGPT", "Bard", "Copilot"
        };

        String windowLower = activeWindow != null ? activeWindow.toLowerCase() : "";

        boolean first = true;
        for (String app : suspiciousApps) {
            if (windowLower.contains(app.toLowerCase())) {
                if (!first) reason.append(", ");
                reason.append(app);
                first = false;
            }
        }

        return reason.toString();
    }
}
