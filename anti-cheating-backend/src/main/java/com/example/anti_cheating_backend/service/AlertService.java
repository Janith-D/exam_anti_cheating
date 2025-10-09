package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.repo.AlertRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class AlertService {
    @Autowired
    private AlertRepo alertRepo;

    public List<Alert> getActiveAlerts(){
        return alertRepo.findByStatusAndTimestampAfter(Enums.AlertStatus.ACTIVE.name(), LocalDateTime.now().minusHours(24));
    }
    public List<Alert> getAlertsByStudent(Long studentId){
        return alertRepo.findByStudentId(studentId);
    }
    public List<Alert> getAlertsBySeverity(Enums.AlertSeverity serverity){
        return alertRepo.findBySeverity(serverity.name());
    }
    public Alert resolveAlert(Long alertId,String resolvedBy){
        Alert alert = alertRepo.findById(alertId)
                .orElseThrow(()-> new RuntimeException("Alert Not found"));
        alert.setStatus(Enums.AlertStatus.RESOLVED);
        alert.setResolvedAt(LocalDateTime.now());
        alert.setResolvedBy(resolvedBy);
        return alertRepo.save(alert);
    }
}
