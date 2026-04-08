package com.example.anti_cheating_backend.dto.identity;

public class IdentityEnrollFaceRequest {
    private String enrollmentToken;
    private String image;

    public IdentityEnrollFaceRequest() {
    }

    public IdentityEnrollFaceRequest(String enrollmentToken, String image) {
        this.enrollmentToken = enrollmentToken;
        this.image = image;
    }

    public String getEnrollmentToken() {
        return enrollmentToken;
    }

    public void setEnrollmentToken(String enrollmentToken) {
        this.enrollmentToken = enrollmentToken;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }
}
