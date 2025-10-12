# Fix: Event Logging 400 Bad Request Error

## Problem
When trying to log an event via `/api/events/log`, you got a 400 Bad Request error with:

```
org.hibernate.TransientObjectException: persistent instance references an unsaved 
transient instance of 'com.example.anti_cheating_backend.entity.ExamSession'
```

## Root Cause

In `EventController.logEvent()`, when an `examSessionId` was provided, the code was creating a **new empty ExamSession object**:

```java
// ❌ WRONG - Creates a new unsaved object
if (examSessionId != null) {
    event.setExamSession(new ExamSession());
}
```

Hibernate tried to save the Event, which referenced this new ExamSession that didn't exist in the database, causing the error.

## Solution

Changed the code to **fetch the existing ExamSession** from the database:

```java
// ✅ CORRECT - Fetches existing ExamSession from database
if (examSessionId != null) {
    ExamSession examSession = examSessionRepo.findById(examSessionId)
            .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
    event.setExamSession(examSession);
}
```

## What Changed

**File:** `EventController.java`

1. Added `@Autowired ExamSessionRepo examSessionRepo`
2. Fixed the `logEvent()` method to fetch the ExamSession instead of creating a new one

## How to Test

### Option 1: Log Event Without Exam Session
```bash
# Using curl (simpler test)
curl.exe -X POST http://localhost:8080/api/events/log \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "studentId=1" \
  -F "type=TAB_SWITCH" \
  -F "details=User switched to another tab"
```

Expected response:
```json
{
  "message": "Event logged",
  "eventId": 1
}
```

### Option 2: Log Event With Exam Session
First, create an exam session, then:

```bash
curl.exe -X POST http://localhost:8080/api/events/log \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -F "studentId=1" \
  -F "type=SNAPSHOT" \
  -F "examSessionId=1" \
  -F "snapshotPath=/path/to/snapshot.jpg"
```

## Postman Testing

**Request:**
- Method: POST
- URL: `http://localhost:8080/api/events/log`
- Headers: 
  - `Authorization: Bearer YOUR_JWT_TOKEN`
- Body: form-data
  - `studentId`: 1
  - `type`: TAB_SWITCH (or any valid EventType)
  - `details`: "Test event" (optional)
  - `snapshotPath`: "/path/to/image" (optional)
  - `examSessionId`: 1 (optional - must be valid exam session ID)

**Expected Response: 200 OK**
```json
{
  "message": "Event logged",
  "eventId": 123
}
```

## Valid Event Types

From your `Enums.java`, valid event types are:
- COPY
- PASTE
- TAB_SWITCH
- WINDOW_BLUR
- WINDOW_FOCUS
- SNAPSHOT
- RIGHT_CLICK
- KEY_COMBINATION
- FULLSCREEN_EXIT
- BROWSER_DEVTOOLS
- MULTIPLE_MONITORS
- SUSPICIOUS_ACTIVITY

## Common Issues

### 1. "Student not found"
**Cause:** Invalid studentId
**Fix:** Use a valid student ID from your database

### 2. "Exam session not found"
**Cause:** Invalid examSessionId or no exam session exists
**Fix:** 
- Either don't send `examSessionId` parameter
- Or create an exam session first
- Or use a valid existing exam session ID

### 3. Still getting 401 Unauthorized
**Cause:** Missing or invalid JWT token
**Fix:** Login first to get a valid token

### 4. Invalid event type
**Cause:** Using an event type that doesn't exist in the enum
**Fix:** Use one of the valid types listed above (case-insensitive)

## What This Endpoint Does

When you log an event:
1. Saves the event to the database
2. Applies rule-based checks (e.g., excessive tab switching)
3. If it's a SNAPSHOT event, queues it for ML verification
4. Creates alerts if suspicious activity is detected
5. Sends real-time notifications via WebSocket

## Notes

- The `examSessionId` parameter is **optional**
- If you provide it, make sure an exam session with that ID exists in the database
- The fix now properly handles both cases:
  - Events without exam session (examSessionId not provided)
  - Events with exam session (examSessionId provided and valid)

---

**The fix is complete!** Your event logging endpoint should now work correctly. 🎉
