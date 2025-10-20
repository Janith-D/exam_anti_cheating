# EXAM SESSION ID NOT SAVING - COMPLETE ANALYSIS & FIX

## Problem Statement:
You're providing `examSessionId` from the extension, but it's not being saved in the database.

---

## Backend Code Analysis:

### 1. Entity (Event.java) - ✅ CORRECT
```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "exam_session_id")
private ExamSession examSession;
```
**Status:** Field exists, properly mapped to `exam_session_id` column.

---

### 2. Controller (EventController.java) - ✅ CORRECT
```java
@PostMapping(value = "/log", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
@PreAuthorize("hasRole('STUDENT')")
public ResponseEntity<?> logEvent(
        @RequestParam("studentId") Long studentId,
        @RequestParam("type") String type,
        @RequestParam(value = "details", required = false) String details,
        @RequestParam(value = "snapshotPath", required = false) String snapshotPath,
        @RequestParam(value = "examSessionId", required = false) Long examSessionId) {
    
    // ... code ...
    
    if (examSessionId != null) {
        ExamSession examSession = examSessionService.findById(examSessionId)
                .orElseThrow(() -> new RuntimeException("Exam session not found: " + examSessionId));
        event.setExamSession(examSession);
    }
    
    Event savedEvent = eventService.logEvent(event);
    // ... rest of code ...
}
```
**Status:** Controller accepts `examSessionId`, looks up ExamSession, sets it on Event.

---

### 3. Service (EventService.java) - ✅ CORRECT
```java
public Event logEvent(Event event){
    event.setTimestamp(LocalDateTime.now());
    event.setIsProcessed(false);
    Event savedEvent = eventRepo.save(event);  // ← Saves event with examSession
    // ... rest of code ...
    return savedEvent;
}
```
**Status:** Service saves the event with all fields including examSession.

---

## Extension Code Analysis:

### Background.js - ✅ CORRECT
```javascript
if (eventData.examSessionId) {
    formData.append('examSessionId', eventData.examSessionId);
}
```
**Status:** Extension sends examSessionId if present.

---

### Content.js - ✅ CORRECT
```javascript
chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
        type: type,
        details: details,
        examSessionId: examSessionId,  // ← Includes examSessionId
        timestamp: new Date().toISOString()
    }
}
```
**Status:** Content script includes examSessionId in the message.

---

## Root Cause Analysis:

There are 3 possible reasons examSessionId is NULL in database:

### Reason 1: examSessionId Variable is NULL in Content Script ❌
**Check:** Look at content.js startup

```javascript
let examSessionId = null;  // ← Initially NULL!

// Restored from storage on load
chrome.storage.local.get(['isMonitoring', 'examSessionId'], (result) => {
  if (result.isMonitoring) {
    isMonitoring = true;
    examSessionId = result.examSessionId;  // ← Might be undefined!
  }
});
```

**Problem:** If `examSessionId` is not in storage, it stays NULL!

---

### Reason 2: Exam Session Doesn't Exist in Database ❌
**Check:** Run this SQL query:

```sql
SELECT * FROM exam_sessions;
```

**If empty:**
- Backend tries to find ExamSession by ID
- Throws RuntimeException: "Exam session not found"
- Event is NOT saved

---

### Reason 3: examSessionId Not Set When Starting Monitoring ❌
**Check:** Popup.js sets examSessionId in storage?

```javascript
// In popup.js - startMonitoring button
chrome.storage.local.set({ 
    isMonitoring: true,
    examSessionId: examSessionId  // ← Is this value correct?
}, () => { ... });
```

**Problem:** If examSessionId field is empty or not read, it's not saved!

---

## THE FIX:

### Step 1: Verify Exam Sessions Exist

**Run this SQL:**
```sql
-- Check if any exam sessions exist
SELECT * FROM exam_sessions;

-- If empty, create one
INSERT INTO exam_sessions (exam_name, start_time, end_time, is_active)
VALUES ('Test Exam', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR), true);

-- Get the ID
SELECT id, exam_name FROM exam_sessions;
```

**Note the exam_session_id!** (e.g., ID = 1)

---

### Step 2: Fix Content.js to Always Load examSessionId

**File:** `content.js`

**Current code:**
```javascript
let examSessionId = null;
```

**Problem:** If storage is empty, stays NULL forever.

**Fix:** Add default value and better logging:

```javascript
let examSessionId = null;

// Load monitoring state from storage on startup
chrome.storage.local.get(['isMonitoring', 'examSessionId'], (result) => {
  if (result.isMonitoring) {
    isMonitoring = true;
    examSessionId = result.examSessionId || null;  // ← Explicit default
    
    console.log('🔴 Restored monitoring state: ACTIVE');
    console.log('📋 Exam Session ID:', examSessionId);  // ← Check this log!
    
    if (!examSessionId) {
      console.warn('⚠️ WARNING: examSessionId is NULL! Events will not have exam session.');
    }
    
    applySelectionBlockingCSS();
  }
});
```

---

### Step 3: Fix Popup.js to Properly Save examSessionId

**File:** `popup/popup.js`

**Find the startMonitoring button handler and verify it reads the input:**

```javascript
startBtn.addEventListener('click', () => {
    chrome.storage.local.get(['jwtToken', 'studentInfo'], (result) => {
      if (!result.jwtToken || !result.studentInfo) {
        showMessage('Please login first', 'error');
        return;
      }
      
      // ✅ FIX: Get exam session ID from input field
      const examSessionIdInput = document.getElementById('examSessionId');
      const examSessionId = examSessionIdInput ? examSessionIdInput.value : null;
      
      // ✅ FIX: Validate it's not empty
      if (!examSessionId) {
        showMessage('Please enter Exam Session ID', 'error');
        return;
      }
      
      // ✅ FIX: Save to storage FIRST
      chrome.storage.local.set({ 
        isMonitoring: true,
        examSessionId: parseInt(examSessionId)  // ← Convert to number!
      }, () => {
        console.log('✅ Saved examSessionId to storage:', examSessionId);
        
        // Then send messages...
        chrome.runtime.sendMessage({ action: "startMonitoring" }, ...);
        
        // Send to all tabs
        chrome.tabs.query({}, (tabs) => {
          tabs.forEach((tab) => {
            chrome.tabs.sendMessage(tab.id, { 
              action: "startMonitoring" 
            });
            
            // ✅ FIX: Also send examSessionId to each tab
            chrome.tabs.sendMessage(tab.id, { 
              action: "setExamSession",
              examSessionId: parseInt(examSessionId)
            });
          });
        });
      });
    });
  });
```

---

### Step 4: Add Logging to Background.js

**File:** `background.js`

**Add this before sending to backend:**

```javascript
console.log('📤 Sending to backend:', {
  studentId: studentInfo.studentId,
  type: eventData.type,
  examSessionId: eventData.examSessionId  // ← Check this!
});
```

---

## Testing Checklist:

### Test 1: Check Exam Sessions Exist
```sql
SELECT * FROM exam_sessions;
```
**Expected:** At least 1 row. Note the `id`.

---

### Test 2: Open Extension Popup
1. Click extension icon
2. Check if "Exam Session ID" input field exists
3. Enter the exam session ID from database (e.g., `1`)
4. Enter Student ID (e.g., `7`)
5. Paste JWT token
6. Click "Save Credentials"
7. Click "Start Monitoring"

---

### Test 3: Check Console Logs
**Open F12 → Console**

**You should see:**
```
Anti-Cheating Extension: Content script loaded
🔴 Restored monitoring state: ACTIVE
📋 Exam Session ID: 1  ← Should NOT be NULL!
```

**If you see:**
```
📋 Exam Session ID: null  ← PROBLEM!
⚠️ WARNING: examSessionId is NULL!
```
**Then:** Extension didn't load examSessionId from storage!

---

### Test 4: Trigger an Event
**Press Ctrl+C**

**Console should show:**
```
🔵 Logging event: KEY_COMBINATION | Blocked: Ctrl+C
📤 Sending to backend: {
  studentId: 7,
  type: "KEY_COMBINATION",
  examSessionId: 1  ← Should be present!
}
✅ Event logged successfully: KEY_COMBINATION
```

---

### Test 5: Check Database
```sql
SELECT id, student_id, exam_session_id, type, details 
FROM events 
WHERE type = 'KEY_COMBINATION' 
ORDER BY timestamp DESC 
LIMIT 5;
```

**Expected Result:**
```
id | student_id | exam_session_id | type             | details
---+------------+-----------------+------------------+------------------
45 | 7          | 1               | KEY_COMBINATION  | Blocked: Ctrl+C
```

**exam_session_id should be 1, NOT NULL!**

---

## Quick Fix Script:

Run this in PowerShell to verify everything:

```powershell
# 1. Check exam sessions
Write-Host "Checking exam sessions..."
# Run in MySQL: SELECT * FROM exam_sessions;

# 2. Check storage in extension
# Open Chrome Console, run:
# chrome.storage.local.get(['examSessionId'], console.log)

# 3. Check latest events
Write-Host "Checking latest events..."
# Run in MySQL:
# SELECT id, student_id, exam_session_id, type 
# FROM events 
# ORDER BY timestamp DESC 
# LIMIT 5;
```

---

## Summary:

**Backend:** ✅ Correctly configured to accept and save examSessionId
**Extension:** ❓ May not be setting examSessionId properly

**The fix is in the EXTENSION, not the backend!**

**Key points:**
1. Create exam session in database first
2. Enter exam session ID in extension popup
3. Verify it's saved to chrome.storage.local
4. Verify content.js loads it on startup
5. Verify it's sent with each event
6. Verify it's saved in database

---

## Next Steps:

1. Run SQL to check exam sessions exist
2. Open extension popup and check console logs
3. Enter exam session ID in popup
4. Start monitoring
5. Check console: `📋 Exam Session ID: 1`
6. Trigger event (Ctrl+C)
7. Check database: `exam_session_id` column should not be NULL

**Report back what you see in the console logs!**
