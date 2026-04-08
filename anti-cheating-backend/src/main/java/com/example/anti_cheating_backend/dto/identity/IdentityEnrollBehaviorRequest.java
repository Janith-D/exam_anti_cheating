package com.example.anti_cheating_backend.dto.identity;

import java.util.Map;

public class IdentityEnrollBehaviorRequest {
    private String enrollmentToken;
    private Map<String, Object> features;

    public IdentityEnrollBehaviorRequest() {
    }

    public IdentityEnrollBehaviorRequest(String enrollmentToken, Map<String, Object> features) {
        this.enrollmentToken = enrollmentToken;
        this.features = features;
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public Map<String, Object> getFeatures() {
        return features;
    }

    public void setFeatures(Map<String, Object> features) {
        this.features = features;
    }
}
