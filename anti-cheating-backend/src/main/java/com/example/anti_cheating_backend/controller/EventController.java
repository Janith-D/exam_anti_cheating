package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Event;
import com.example.anti_cheating_backend.service.EventService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/events")
public class EventController {

    @Autowired
    private EventService eventService;

    @PostMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<?> logEvent(@RequestBody Event event){
        try{
            Event savedEvent = eventService.logEvent(event);
            return ResponseEntity.ok(Map.of("message","Event logged","eventId",savedEvent.getId()));
        } catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }
    @GetMapping("/student/{studentId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getEventsByStudent(@PathVariable Long studentId,
                                                @RequestParam String startTime,
                                                @RequestParam String endTime){
        try{
            LocalDateTime start = LocalDateTime.parse(startTime);
            LocalDateTime end = LocalDateTime.parse(endTime);
            List<Event> events = eventService.getEventsByStudent(studentId,start,end);
            return ResponseEntity.ok(events);
        } catch (RuntimeException e){
            return ResponseEntity.badRequest().body(Map.of("error",e.getMessage()));
        }
    }
}
