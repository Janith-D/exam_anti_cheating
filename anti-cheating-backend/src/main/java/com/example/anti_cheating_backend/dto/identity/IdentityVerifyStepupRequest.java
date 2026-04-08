package com.example.anti_cheating_backend.dto.identity;

public class IdentityVerifyStepupRequest {
    private String sessionId;
    private String trigger;

    public IdentityVerifyStepupRequest() {
    }

    public IdentityVerifyStepupRequest(String sessionId, String trigger) {
        this.sessionId = sessionId;
        this.trigger = trigger;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getTrigger() {
        return trigger;
    }

    public void setTrigger(String trigger) {
        this.trigger = trigger;
    }
}
