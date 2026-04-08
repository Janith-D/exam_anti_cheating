package com.example.anti_cheating_backend.dto.identity;

public class IdentityEnrollFinalizeRequest {
    private String enrollmentToken;

    public IdentityEnrollFinalizeRequest() {
    }

    public IdentityEnrollFinalizeRequest(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }
}
