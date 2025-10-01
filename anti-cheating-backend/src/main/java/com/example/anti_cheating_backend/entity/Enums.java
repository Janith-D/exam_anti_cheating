package com.example.anti_cheating_backend.entity;

public class Enums {
    public enum UserRole {
        STUDENT, ADMIN, PROCTOR
    }

    public enum EventType {
        COPY, PASTE, TAB_SWITCH, WINDOW_BLUR, WINDOW_FOCUS,
        SNAPSHOT, RIGHT_CLICK, KEY_COMBINATION, FULLSCREEN_EXIT,
        BROWSER_DEVTOOLS, MULTIPLE_MONITORS, SUSPICIOUS_ACTIVITY
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
}
