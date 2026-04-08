package com.example.anti_cheating_backend.dto.identity;

import java.util.Map;

public class IdentityMonitorPingRequest {
    private String sessionId;
    private Map<String, Object> signals;

    public IdentityMonitorPingRequest() {
    }

    public IdentityMonitorPingRequest(String sessionId, Map<String, Object> signals) {
        this.sessionId = sessionId;
        this.signals = signals;
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Map<String, Object> getSignals() {
        return signals;
    }

    public void setSignals(Map<String, Object> signals) {
        this.signals = signals;
    }
}
