package com.example.anti_cheating_backend.dto.identity;

public class IdentityEnrollVoiceRequest {
    private String enrollmentToken;
    private String audio;

    public IdentityEnrollVoiceRequest() {
    }

    public IdentityEnrollVoiceRequest(String enrollmentToken, String audio) {
        this.enrollmentToken = enrollmentToken;
        this.audio = audio;
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public String getAudio() {
        return audio;
    }

    public void setAudio(String audio) {
        this.audio = audio;
    }
}
