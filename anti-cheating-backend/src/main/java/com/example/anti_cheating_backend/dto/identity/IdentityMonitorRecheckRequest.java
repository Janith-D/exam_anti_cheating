package com.example.anti_cheating_backend.dto.identity;

public class IdentityMonitorRecheckRequest {
    private String sessionId;
    private Long studentId;
    private String image;

    public IdentityMonitorRecheckRequest() {
    }

    public IdentityMonitorRecheckRequest(String sessionId, Long studentId, String image) {
        this.sessionId = sessionId;
        this.studentId = studentId;
        this.image = image;
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
}
