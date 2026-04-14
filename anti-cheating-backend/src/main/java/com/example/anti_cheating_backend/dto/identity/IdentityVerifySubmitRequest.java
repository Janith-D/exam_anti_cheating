package com.example.anti_cheating_backend.dto.identity;

import java.util.Map;

public class IdentityVerifySubmitRequest {
    private String sessionId;
    private Long studentId;
    private String image;
    private String audio;
    private Map<String, Object> behavior;
    private Map<String, Object> challenge;

    public IdentityVerifySubmitRequest() {
    }

    public IdentityVerifySubmitRequest(
            String sessionId,
            Long studentId,
            String image,
            String audio,
            Map<String, Object> behavior,
            Map<String, Object> challenge
    ) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.image = image;
        this.audio = audio;
        this.behavior = behavior;
        this.challenge = challenge;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public String getAudio() {
        return audio;
    }

    public void setAudio(String audio) {
        this.audio = audio;
    }

    public Map<String, Object> getBehavior() {
        return behavior;
    }

    public void setBehavior(Map<String, Object> behavior) {
        this.behavior = behavior;
    }

    public Map<String, Object> getChallenge() {
        return challenge;
    }

    public void setChallenge(Map<String, Object> challenge) {
        this.challenge = challenge;
    }
}
