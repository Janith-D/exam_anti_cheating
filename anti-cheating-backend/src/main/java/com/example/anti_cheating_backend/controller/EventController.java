package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.service.EventService;
import com.example.anti_cheating_backend.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @Autowired
    private StudentService studentService;

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
            event.setType(Enums.EventType.valueOf(type.toUpperCase()));
            event.setDetails(details);
            event.setSnapshotPath(snapshotPath);
            if (examSessionId != null) {
                event.setExamSession(new ExamSession());
            }
            Event savedEvent = eventService.logEvent(event);
            return ResponseEntity.ok(Map.of("message", "Event logged", "eventId", savedEvent.getId()));
        } catch (RuntimeException e) {
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
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}