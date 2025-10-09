package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.repo.AlertRepo;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.EventRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class EventService {

    @Autowired
    private EventRepo eventRepo;

    @Autowired
    private AlertRepo alertRepo;

    @Autowired
    private EnrollmentRepo enrollmentRepo;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlService;

    private final RestTemplate restTemplate = new RestTemplate();

    public Event logEvent(Event event){
        event.setTimestamp(LocalDateTime.now());
        event.setIsProcessed(false);
        Event savedEvent = eventRepo.save(event);

        applyRules(savedEvent);
        if (event.getType()== Enums.EventType.SNAPSHOT && event.getSnapshotPath() != null){
            enqueueToML(savedEvent);
        }
        return  savedEvent;
    }
    public void applyRules(Event event){
        LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

        //tab switch rule
        Long tabSwitchCount = eventRepo.countByStudentIdAndTypeAndTimestampBetween(
                event.getStudent().getId(), Enums.EventType.TAB_SWITCH,fiveMinutesAgo,LocalDateTime.now());
        if (tabSwitchCount > 2){
            createAlert(event,"Excessive tab switching ("+tabSwitchCount+"in 5 min)",Enums.AlertSeverity.HIGH);
        }

        //copy/paste rule
        Long copyPasteCount = eventRepo.countByStudentIdAndTypesAndTimestampBetweens(
                event.getStudent().getId(),List.of(Enums.EventType.COPY,Enums.EventType.PASTE),fiveMinutesAgo,LocalDateTime.now());
        if (copyPasteCount>2){
            createAlert(event,"Suspicious copy/paste Activity ("+ copyPasteCount+" events)",Enums.AlertSeverity.MEDIUM);
        }

        //Right-click rule
        if(event.getType()== Enums.EventType.RIGHT_CLICK){
            long rightClickCount = eventRepo.countByStudentIdAndTypeAndTimestampBetween(
                    event.getStudent().getId(),Enums.EventType.RIGHT_CLICK,fiveMinutesAgo,LocalDateTime.now());
            if (rightClickCount > 5){
                createAlert(event,"Excessive right-clicks ("+rightClickCount+" in 5 min)",Enums.AlertSeverity.LOW);
            }
        }
        //add more rules as needed
    }
    private void enqueueToML(Event event){
        Enrollment enrollment = enrollmentRepo.findByStudentId(event.getStudent().getId());
        if (enrollment== null){
            createAlert(event,"No enrollment for identity verification",Enums.AlertSeverity.MEDIUM);
            return;
        }
        String snapshotPath = event.getSnapshotPath();
        String storeEmbedding = enrollment.getFaceEmbedding();

        try{
            Map<String,Object> payload = Map.of(
                    "studentId",event.getStudent().getId(),
                    "image",snapshotPath,
                    "storedEmbedding",storeEmbedding,
                    "examSessionId",event.getExamSession() != null ? event.getExamSession().getId():null

            );
            ResponseEntity<Map> response = restTemplate.postForEntity(mlService + "/verify",payload, Map.class);
            Map<String,Object> result = response.getBody();

            boolean match = Boolean.parseBoolean(result.get("match").toString());
            boolean liveness = Boolean.parseBoolean(result.get("liveness").toString());

            if(!match || !liveness){
                String message = "Snapshot verification failed: "+(!match ? "Identity mismatch" : "Liveness check failed");
                createAlert(event,message,Enums.AlertSeverity.CRITICAL);
            }else {
                event.setIsProcessed(true);
                eventRepo.save(event);
            }
        }catch (Exception e){
            createAlert(event,"ML service error: "+ e.getMessage(),Enums.AlertSeverity.CRITICAL);
        }
    }
    private void createAlert(Event event,String message,Enums.AlertSeverity severity){
        Alert alert = new Alert();
        alert.setEvent(event);
        alert.setStudent(event.getStudent());
        alert.setSeverity(severity);
        alert.setMessage(message);
        alert.setStatus(Enums.AlertStatus.ACTIVE);
        alert.setTimestamp(LocalDateTime.now());
        alertRepo.save(alert);

        messagingTemplate.convertAndSend("/topic/alerts",alert);
    }
}
