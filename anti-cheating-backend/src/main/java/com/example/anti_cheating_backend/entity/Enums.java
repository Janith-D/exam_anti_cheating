package com.example.anti_cheating_backend.entity;

public class Enums {
    public enum UserRole {
        STUDENT, ADMIN, PROCTOR
    }

    public enum EventType {
        COPY, PASTE, TAB_SWITCH, WINDOW_BLUR, WINDOW_FOCUS,
        SNAPSHOT, RIGHT_CLICK, KEY_COMBINATION, FULLSCREEN_EXIT,
        BROWSER_DEVTOOLS, MULTIPLE_MONITORS, SUSPICIOUS_ACTIVITY,
        // Real-time activity types
        FACE_DETECTED, MULTIPLE_FACES, NO_FACE, MOUSE_LEAVE,
        COPY_ATTEMPT, PASTE_ATTEMPT, QUESTION_ANSWERED, 
        TEST_STARTED, TEST_SUBMITTED, CAMERA_PERMISSION_DENIED,
        MICROPHONE_PERMISSION_DENIED
    }

    public enum AlertSeverity {
        LOW, MEDIUM, HIGH, CRITICAL
    }

    public enum AlertStatus {
        ACTIVE, RESOLVED, DISMISSED
    }

    public enum SessionStatus {
        SCHEDULED, ACTIVE, COMPLETED, CANCELLED
    }

    public enum ExamStatus {
        DRAFT, PUBLISHED, ONGOING, COMPLETED, ARCHIVED
    }

    public enum EnrollmentStatus {
        PENDING, VERIFIED, APPROVED, REJECTED
    }
}
