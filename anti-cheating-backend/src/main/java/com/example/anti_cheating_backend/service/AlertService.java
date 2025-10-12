package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enums.AlertSeverity;
import com.example.anti_cheating_backend.entity.Enums.AlertStatus;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.AlertRepo;
import com.example.anti_cheating_backend.websocket.WebSocketService;

@Service
public class AlertService {

    private static final Logger LOGGER = Logger.getLogger(AlertService.class.getName());

    @Autowired
    private AlertRepo alertRepo;

    @Autowired
    private WebSocketService webSocketService;

    public List<Alert> getActiveAlerts() {
        return alertRepo.findByStatusAndTimestampAfter(AlertStatus.ACTIVE, LocalDateTime.now().minusHours(24));
    }

    public List<Alert> getAlertsByStudent(Long studentId) {
        return alertRepo.findByStudentId(studentId);
    }

    public List<Alert> getAlertsBySeverity(AlertSeverity severity) {
        return alertRepo.findBySeverity(severity);
    }

    public Alert resolveAlert(Long alertId, String resolvedBy) {
        Alert alert = alertRepo.findById(alertId)
                .orElseThrow(() -> new RuntimeException("Alert not found"));
        alert.setStatus(AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);
        return alertRepo.save(alert);
    }

    public Alert createAlert(Student student, ExamSession session, String alertType, String description, AlertSeverity severity) {
        Alert alert = new Alert();
        alert.setStudent(student);
        alert.setExamSession(session);
        alert.setMessage(alertType);
        alert.setDescription(description);
        alert.setSeverity(severity);
        alert.setStatus(AlertStatus.ACTIVE);
        alert.setTimestamp(LocalDateTime.now());

        Alert savedAlert = alertRepo.save(alert);
        LOGGER.info(String.format("Created alert: %d, type: %s", savedAlert.getId(), alertType));

        // Broadcast alert via WebSocket
        webSocketService.sendAlert(savedAlert);
        return savedAlert;
    }
}