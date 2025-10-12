package com.example.anti_cheating_backend.websocket;

import com.example.anti_cheating_backend.entity.Alert;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.logging.Logger;

@Service
public class WebSocketService {

    private static final Logger LOGGER = Logger.getLogger(WebSocketService.class.getName());

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    public void sendAlert(Alert alert){
        try{
            messagingTemplate.convertAndSend("/topic/alerts",alert);
            LOGGER.info("Sent alert to /topic/alerts: " + alert.getId());
        } catch (Exception e){
            LOGGER.severe("Error sending alert via WebSocket: "+ e.getMessage());
        }
    }
    public void SendProctoringUpdate(Long studentId, String update){
        try{
            messagingTemplate.convertAndSend("/topic/proctor/"+studentId,update);
            LOGGER.info("Send proctoring update to /topic/proctor/"+studentId+ ": "+ update);
        } catch (Exception e){
            LOGGER.severe("Error sending proctoring update : "+ e.getMessage());
        }
    }
}
