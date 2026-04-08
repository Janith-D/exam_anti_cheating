package com.example.anti_cheating_backend.dto.identity;

public class IdentityVerifyStartRequest {
    private Long studentId;
    private Long examSessionId;
    private Integer ttlSeconds;

    public IdentityVerifyStartRequest() {
    }

    public IdentityVerifyStartRequest(Long studentId, Long examSessionId, Integer ttlSeconds) {
        this.studentId = studentId;
        this.examSessionId = examSessionId;
        this.ttlSeconds = ttlSeconds;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public Long getExamSessionId() {
        return examSessionId;
    }

    public void setExamSessionId(Long examSessionId) {
        this.examSessionId = examSessionId;
    }

    public Integer getTtlSeconds() {
        return ttlSeconds;
    }

    public void setTtlSeconds(Integer ttlSeconds) {
        this.ttlSeconds = ttlSeconds;
    }
}
