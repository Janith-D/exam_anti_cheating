package com.example.anti_cheating_backend.dto;

public class StudentActivityDTO {
    private Long studentId;
    private String studentName;
    private String studentEmail;
    private Long sessionId;
    private Long testId;
    private String testName;
    private String activityType;  // FACE_DETECTED, TAB_SWITCH, COPY_ATTEMPT, PASTE_ATTEMPT, MOUSE_LEAVE, MULTIPLE_FACES, NO_FACE, QUESTION_ANSWERED, TEST_STARTED, TEST_SUBMITTED
    private String severity;      // LOW, MEDIUM, HIGH, CRITICAL
    private String description;
    private String metadata;      // JSON string with additional data
    private String timestamp;     // Client timestamp
    private String serverTimestamp; // Server timestamp
    
    // Constructors
    public StudentActivityDTO() {}
    
    public StudentActivityDTO(Long studentId, String studentName, String activityType, String severity, String description) {
        this.studentId = studentId;
        this.studentName = studentName;
        this.activityType = activityType;
        this.severity = severity;
        this.description = description;
    }
    
    // Getters and Setters
    public Long getStudentId() {
        return studentId;
    }
    
    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }
    
    public String getStudentName() {
        return studentName;
    }
    
    public void setStudentName(String studentName) {
        this.studentName = studentName;
    }
    
    public String getStudentEmail() {
        return studentEmail;
    }
    
    public void setStudentEmail(String studentEmail) {
        this.studentEmail = studentEmail;
    }
    
    public Long getSessionId() {
        return sessionId;
    }
    
    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }
    
    public Long getTestId() {
        return testId;
    }
    
    public void setTestId(Long testId) {
        this.testId = testId;
    }
    
    public String getTestName() {
        return testName;
    }
    
    public void setTestName(String testName) {
        this.testName = testName;
    }
    
    public String getActivityType() {
        return activityType;
    }
    
    public void setActivityType(String activityType) {
        this.activityType = activityType;
    }
    
    public String getSeverity() {
        return severity;
    }
    
    public void setSeverity(String severity) {
        this.severity = severity;
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
    
    public String getMetadata() {
        return metadata;
    }
    
    public void setMetadata(String metadata) {
        this.metadata = metadata;
    }
    
    public String getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    
    public String getServerTimestamp() {
        return serverTimestamp;
    }
    
    public void setServerTimestamp(String serverTimestamp) {
        this.serverTimestamp = serverTimestamp;
    }
    
    @Override
    public String toString() {
        return "StudentActivityDTO{" +
                "studentId=" + studentId +
                ", studentName='" + studentName + '\'' +
                ", sessionId=" + sessionId +
                ", activityType='" + activityType + '\'' +
                ", severity='" + severity + '\'' +
                ", description='" + description + '\'' +
                ", timestamp='" + timestamp + '\'' +
                '}';
    }
}
