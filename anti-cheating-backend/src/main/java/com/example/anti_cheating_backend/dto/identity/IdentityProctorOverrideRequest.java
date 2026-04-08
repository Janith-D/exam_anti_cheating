package com.example.anti_cheating_backend.dto.identity;

public class IdentityProctorOverrideRequest {
    private String sessionId;
    private String decision;
    private String reason;
    private String proctorId;

    public IdentityProctorOverrideRequest() {
    }

    public IdentityProctorOverrideRequest(String sessionId, String decision, String reason, String proctorId) {
        this.sessionId = sessionId;
        this.decision = decision;
        this.reason = reason;
        this.proctorId = proctorId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getProctorId() {
        return proctorId;
    }

    public void setProctorId(String proctorId) {
        this.proctorId = proctorId;
    }
}
