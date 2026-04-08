package com.example.anti_cheating_backend.dto.identity;

public class IdentityEnrollStartRequest {
    private Long studentId;

    public IdentityEnrollStartRequest() {
    }

    public IdentityEnrollStartRequest(Long studentId) {
        this.studentId = studentId;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
}
