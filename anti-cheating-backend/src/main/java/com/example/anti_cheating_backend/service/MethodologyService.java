package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Methodology;
import com.example.anti_cheating_backend.repo.MethodologyRepo;

@Service
public class MethodologyService {

    @Autowired
    private MethodologyRepo methodologyRepo;

    public List<Methodology> getAllMethodologies() {
        return methodologyRepo.findAll();
    }

    public Methodology getMethodologyById(Long id) {
        return methodologyRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Methodology not found with id: " + id));
    }

    public List<Methodology> getActiveMethodologies() {
        return methodologyRepo.findByStatus(Enums.MethodologyStatus.ACTIVE);
    }

    public Methodology createMethodology(Methodology methodology) {
        methodology.setCreatedAt(LocalDateTime.now());
        return methodologyRepo.save(methodology);
    }

    public Methodology updateMethodology(Long id, Methodology details) {
        Methodology methodology = getMethodologyById(id);

        if (details.getName() != null) {
            methodology.setName(details.getName());
        }
        if (details.getDescription() != null) {
            methodology.setDescription(details.getDescription());
        }
        if (details.getType() != null) {
            methodology.setType(details.getType());
        }
        if (details.getMonitoringLevel() != null) {
            methodology.setMonitoringLevel(details.getMonitoringLevel());
        }
        if (details.getStatus() != null) {
            methodology.setStatus(details.getStatus());
        }
        if (details.getAlertThreshold() != null) {
            methodology.setAlertThreshold(details.getAlertThreshold());
        }

        return methodologyRepo.save(methodology);
    }

    public void deleteMethodology(Long id) {
        Methodology methodology = getMethodologyById(id);
        methodologyRepo.delete(methodology);
    }

    public Methodology activateMethodology(Long id) {
        Methodology methodology = getMethodologyById(id);
        methodology.setStatus(Enums.MethodologyStatus.ACTIVE);
        return methodologyRepo.save(methodology);
    }

    public Methodology deactivateMethodology(Long id) {
        Methodology methodology = getMethodologyById(id);
        methodology.setStatus(Enums.MethodologyStatus.INACTIVE);
        return methodologyRepo.save(methodology);
    }
}
