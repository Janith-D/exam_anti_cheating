package com.example.anti_cheating_backend.controller;

import java.time.LocalDateTime;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseBody;

import com.example.anti_cheating_backend.dto.StudentActivityDTO;
import com.example.anti_cheating_backend.service.EventService;

@Controller
@CrossOrigin(origins = "http://localhost:4200")
public class StudentActivityController {

    private static final Logger LOGGER = Logger.getLogger(StudentActivityController.class.getName());

    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    
    @Autowired
    private EventService eventService;

    /**
     * Receive student activity from WebSocket and broadcast to admin dashboard
     * Students send to: /app/student-activity
     * Admins subscribe to: /topic/student-activity
     */
    @MessageMapping("/student-activity")
    public void handleStudentActivity(@Payload StudentActivityDTO activity) {
        try {
            // Add server timestamp
            activity.setServerTimestamp(LocalDateTime.now().toString());
            
            LOGGER.info("📡 Received student activity: " + activity.getActivityType() + 
                       " from student: " + activity.getStudentId() + 
                       " (Session: " + activity.getSessionId() + ")");
            
            // Broadcast to all admins subscribed to /topic/student-activity
            messagingTemplate.convertAndSend("/topic/student-activity", activity);
            
            // Also send to specific session topic if admin filtering by session
            if (activity.getSessionId() != null) {
                messagingTemplate.convertAndSend("/topic/session/" + activity.getSessionId() + "/activity", activity);
            }
            
            // Persist activity to database for historical tracking
            try {
                eventService.saveActivity(activity);
                LOGGER.info("💾 Activity saved to database: " + activity.getActivityType());
            } catch (Exception e) {
                LOGGER.warning("⚠️ Failed to save activity to database: " + e.getMessage());
            }
            
        } catch (Exception e) {
            LOGGER.severe("Error handling student activity: " + e.getMessage());
        }
    }

    /**
     * REST endpoint for manual activity submission (fallback if WebSocket fails)
     */
    @PostMapping("/api/student-activity")
    @ResponseBody
    public String submitActivity(@RequestBody StudentActivityDTO activity) {
        try {
            activity.setServerTimestamp(LocalDateTime.now().toString());
            
            LOGGER.info("📡 REST: Received student activity: " + activity.getActivityType() + 
                       " from student: " + activity.getStudentId());
            
            // Broadcast via WebSocket
            messagingTemplate.convertAndSend("/topic/student-activity", activity);
            
            if (activity.getSessionId() != null) {
                messagingTemplate.convertAndSend("/topic/session/" + activity.getSessionId() + "/activity", activity);
            }
            
            // Persist activity to database
            try {
                eventService.saveActivity(activity);
                LOGGER.info("💾 REST: Activity saved to database: " + activity.getActivityType());
            } catch (Exception e) {
                LOGGER.warning("⚠️ REST: Failed to save activity to database: " + e.getMessage());
            }
            
            return "Activity received";
        } catch (Exception e) {
            LOGGER.severe("Error in REST activity submission: " + e.getMessage());
            return "Error: " + e.getMessage();
        }
    }
}
