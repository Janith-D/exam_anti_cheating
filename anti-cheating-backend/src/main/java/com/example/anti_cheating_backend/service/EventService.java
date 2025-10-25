package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import com.example.anti_cheating_backend.dto.StudentActivityDTO;
import com.example.anti_cheating_backend.entity.Alert;
import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.AlertRepo;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.EventRepo;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;

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
    
    @Autowired
    private StudentRepo studentRepo;
    
    @Autowired
    private ExamSessionRepo examSessionRepo;

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
    public List<Event> getEventsByStudent(Long studentId, LocalDateTime start, LocalDateTime end) {
        // Validate input parameters
        if (studentId == null || start == null || end == null || start.isAfter(end)) {
            throw new IllegalArgumentException("Invalid input parameters for retrieving events.");
        }

        // Add logging
        System.out.println("Fetching events for studentId: " + studentId);
        System.out.println("Start time: " + start);
        System.out.println("End time: " + end);
        
        // Fetch events from the repository
        List<Event> events = eventRepo.findByStudentIdAndTimestampBetween(studentId, start, end);
        
        System.out.println("Found " + events.size() + " events");
        
        return events;
    }
    
    public List<Event> getAllEventsByStudent(Long studentId) {
        // Get all events for a student without date filtering
        return eventRepo.findAll().stream()
                .filter(event -> event.getStudent().getId().equals(studentId))
                .toList();
    }
    
    public List<Event> getEventsBySession(Long sessionId) {
        if (sessionId == null) {
            throw new IllegalArgumentException("Session ID cannot be null");
        }
        
        System.out.println("Fetching events for sessionId: " + sessionId);
        
        // Fetch events from the repository filtered by exam session
        List<Event> events = eventRepo.findAll().stream()
                .filter(event -> event.getExamSession() != null && event.getExamSession().getId().equals(sessionId))
                .toList();
        
        System.out.println("Found " + events.size() + " events for session " + sessionId);
        
        return events;
    }
    
    /**
     * Save a student activity from WebSocket to the database as an Event
     */
    public Event saveActivity(StudentActivityDTO activityDTO) {
        try {
            // Fetch Student and ExamSession entities
            Student student = null;
            ExamSession examSession = null;
            
            if (activityDTO.getStudentId() != null) {
                student = studentRepo.findById(activityDTO.getStudentId()).orElse(null);
                if (student == null) {
                    System.err.println("Student not found for ID: " + activityDTO.getStudentId());
                    return null;
                }
            }
            
            if (activityDTO.getSessionId() != null) {
                examSession = examSessionRepo.findById(activityDTO.getSessionId()).orElse(null);
                if (examSession == null) {
                    System.err.println("Exam session not found for ID: " + activityDTO.getSessionId());
                    return null;
                }
            }
            
            // Create Event entity
            Event event = new Event();
            event.setStudent(student);
            event.setExamSession(examSession);
            
            // Map activityType string to EventType enum
            event.setType(mapActivityTypeToEventType(activityDTO.getActivityType()));
            
            // Set details
            event.setDetails(activityDTO.getDescription() != null ? activityDTO.getDescription() : activityDTO.getActivityType());
            
            // Map severity string to integer level
            event.setSeverityLevel(mapSeverityToLevel(activityDTO.getSeverity()));
            
            // Parse timestamp
            if (activityDTO.getServerTimestamp() != null) {
                event.setTimestamp(parseTimestamp(activityDTO.getServerTimestamp()));
            } else if (activityDTO.getTimestamp() != null) {
                event.setTimestamp(parseTimestamp(activityDTO.getTimestamp()));
            } else {
                event.setTimestamp(LocalDateTime.now());
            }
            
            // Set as processed (not requiring ML verification for activity events)
            event.setIsProcessed(true);
            
            // Save to database
            Event savedEvent = eventRepo.save(event);
            System.out.println("Activity saved: " + savedEvent.getType() + " for student " + 
                             (student != null ? student.getId() : "unknown"));
            
            return savedEvent;
            
        } catch (Exception e) {
            System.err.println("Error saving activity: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }
    
    /**
     * Map activityType string to EventType enum
     */
    private Enums.EventType mapActivityTypeToEventType(String activityType) {
        if (activityType == null) {
            return Enums.EventType.SUSPICIOUS_ACTIVITY;
        }
        
        try {
            // Try direct enum match
            return Enums.EventType.valueOf(activityType.toUpperCase());
        } catch (IllegalArgumentException e) {
            // Handle legacy or unmapped types
            switch (activityType.toUpperCase()) {
                case "TAB_CHANGE":
                    return Enums.EventType.TAB_SWITCH;
                case "COPY":
                case "COPY_DETECTED":
                    return Enums.EventType.COPY_ATTEMPT;
                case "PASTE":
                case "PASTE_DETECTED":
                    return Enums.EventType.PASTE_ATTEMPT;
                default:
                    System.err.println("Unknown activity type: " + activityType + ", defaulting to SUSPICIOUS_ACTIVITY");
                    return Enums.EventType.SUSPICIOUS_ACTIVITY;
            }
        }
    }
    
    /**
     * Map severity string to integer level
     */
    private Integer mapSeverityToLevel(String severity) {
        if (severity == null) {
            return 1;
        }
        
        switch (severity.toUpperCase()) {
            case "LOW":
                return 1;
            case "MEDIUM":
                return 2;
            case "HIGH":
                return 3;
            case "CRITICAL":
                return 4;
            default:
                return 1;
        }
    }
    
    /**
     * Parse timestamp string to LocalDateTime
     */
    private LocalDateTime parseTimestamp(String timestamp) {
        if (timestamp == null) {
            return LocalDateTime.now();
        }
        
        try {
            // Try ISO format first (e.g., "2024-01-10T15:30:45")
            return LocalDateTime.parse(timestamp);
        } catch (DateTimeParseException e1) {
            try {
                // Try custom format with formatter
                DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
                return LocalDateTime.parse(timestamp, formatter);
            } catch (DateTimeParseException e2) {
                System.err.println("Could not parse timestamp: " + timestamp + ", using current time");
                return LocalDateTime.now();
            }
        }
    }
}
