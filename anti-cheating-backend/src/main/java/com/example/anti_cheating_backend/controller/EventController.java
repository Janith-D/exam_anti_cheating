package com.example.anti_cheating_backend.controller;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Enums.AlertSeverity;
import com.example.anti_cheating_backend.entity.Enums.EventType;
import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.service.AlertService;
import com.example.anti_cheating_backend.service.EventService;
import com.example.anti_cheating_backend.service.ExamSessionService;
import com.example.anti_cheating_backend.service.StudentService;

@RestController
@RequestMapping("/api/events")
public class EventController {

    private static final Logger LOGGER = Logger.getLogger(EventController.class.getName());

    @Autowired
    private EventService eventService;

    @Autowired
    private StudentService studentService;

    @Autowired
    private ExamSessionService examSessionService;

    @Autowired
    private AlertService alertService;

    @PostMapping(value = "/log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> logEvent(
            @RequestParam("studentId") Long studentId,
            @RequestParam("type") String type,
            @RequestParam(value = "details", required = false) String details,
            @RequestParam(value = "snapshotPath", required = false) String snapshotPath,
            @RequestParam(value = "examSessionId", required = false) Long examSessionId) {
        try {
            Student student = studentService.findById(studentId)
                    .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

            Event event = new Event();
            event.setStudent(student);
            event.setType(EventType.valueOf(type.toUpperCase()));
            event.setDetails(details);
            event.setSnapshotPath(snapshotPath);

            if (examSessionId != null) {
                ExamSession examSession = examSessionService.findById(examSessionId)
                        .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
                event.setExamSession(examSession);
            }

            Event savedEvent = eventService.logEvent(event);
            LOGGER.info(String.format("Logged event: %d, type: %s", savedEvent.getId(), type));

            // Trigger alert for suspicious events
            if (type.equals("TAB_SWITCH") || type.equals("COPY") || type.equals("PASTE")) {
                alertService.createAlert(student, event.getExamSession(), type, details, AlertSeverity.HIGH);
            }

            return ResponseEntity.ok(Map.of("message", "Event logged", "eventId", savedEvent.getId()));
        } catch (RuntimeException e) {
            LOGGER.severe(String.format("Error logging event: %s", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEventsByStudent(
            @PathVariable Long studentId,
            @RequestParam String startTime,
            @RequestParam String endTime) {
        try {
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            List<Event> events = eventService.getEventsByStudent(studentId, start, end);
            return ResponseEntity.ok(events);
        } catch (RuntimeException e) {
            LOGGER.severe(String.format("Error fetching events: %s", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/student/{studentId}/all")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<?> getAllEventsByStudent(@PathVariable Long studentId) {
        try {
            List<Event> events = eventService.getAllEventsByStudent(studentId);
            return ResponseEntity.ok(Map.of(
                    "studentId", studentId,
                    "eventCount", events.size(),
                    "events", events
            ));
        } catch (RuntimeException e) {
            LOGGER.severe(String.format("Error fetching all events: %s", e.getMessage()));
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}