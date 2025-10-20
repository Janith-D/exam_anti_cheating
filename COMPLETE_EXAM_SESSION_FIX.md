# 🔴 EXAM SESSION ID NOT SAVING - COMPLETE FIX

## Your Issue:
> "i provide examId not saving database examId extension seaid provide examSession id but it has error"

**Translation:** You enter examSessionId in the extension, but it's NOT being saved in the database `exam_session_id` column.

---

## 🎯 ROOT CAUSE FOUND:

After analyzing ALL the code, the backend is **100% correct**. The issue is:

### ❌ **Backend is NOT Running!**

When I ran the diagnostic, I got:
```
Backend is NOT running!
```

**Without the backend running:**
- Extension can't log events
- You get 401 errors
- examSessionId can't be saved
- Everything fails

---

## ✅ THE COMPLETE FIX:

### Step 1: Start the Backend

**Open PowerShell:**
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
mvn spring-boot:run
```

**Wait for:**
```
Started AntiCheatingBackendApplication in X.XXX seconds
```

**Keep this terminal open!**

---

### Step 2: Create Exam Session in Database

**Open MySQL Workbench and run:**
```sql
-- Create an exam session
INSERT INTO exam_sessions (exam_name, start_time, end_time, is_active)
VALUES ('Test Exam Session', NOW(), DATE_ADD(NOW(), INTERVAL 2 HOUR), true);

-- Get the ID
SELECT id, exam_name, is_active FROM exam_sessions;
```

**Note the `id`** (e.g., `1`)

---

### Step 3: Register/Login to Get Fresh Token

**Method A: Using Browser Console (Easiest)**

1. Open any webpage in Chrome
2. Press **F12** → Console tab
3. Paste this code:

```javascript
fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'student_' + Date.now(),
    email: 'student' + Date.now() + '@test.com',
    password: 'test123',
    role: 'STUDENT'
  })
})
.then(r => r.json())
.then(data => {
  console.log('✅ Token:', data.token);
  console.log('✅ Student ID:', data.userId);
  navigator.clipboard.writeText(data.token);
  alert('Token copied!\nStudent ID: ' + data.userId);
})
.catch(err => console.error('Error:', err));
```

4. **Copy the Student ID from the alert**
5. **Token is already in clipboard!**

---

### Step 4: Configure Extension Properly

1. **Reload extension:**
   - Go to `chrome://extensions/`
   - Find "Anti-Cheating Proctor Extension"
   - Click **Reload** (↻)

2. **Click extension icon**

3. **Enter credentials:**
   - **Student ID:** (from the alert, e.g., `9`)
   - **Exam Session ID:** (from database, e.g., `1`)
   - **JWT Token:** **Ctrl+V** (paste from clipboard)

4. **Click "Save Credentials"**
   - Should see: ✅ "Credentials saved successfully!"

5. **Click "Start Monitoring"**
   - Should see: ✅ "Monitoring started successfully!"

---

### Step 5: Verify examSessionId is Loaded

**Open Console (F12) on any webpage**

**You should see:**
```
Anti-Cheating Extension: Content script loaded
🔴 Restored monitoring state: ACTIVE
📋 Exam Session ID: 1  ← Should show the ID you entered!
Applied selection blocking CSS
```

**If you see:**
```
📋 Exam Session ID: null
```

**Then run this in console:**
```javascript
chrome.storage.local.get(['examSessionId'], console.log)
```

**Should show:**
```javascript
{ examSessionId: "1" }
```

**If empty, extension didn't save it!** Go back to Step 4.

---

### Step 6: Test Event Logging

1. **Press Ctrl+C** on the page

2. **Check console, should see:**
```
🔵 Logging event: KEY_COMBINATION | Blocked: Ctrl+C
📨 Received logEvent request: KEY_COMBINATION
✅ Event logged successfully to backend: KEY_COMBINATION
📊 Event count updated: 1
```

3. **NO 401 ERRORS!**

---

### Step 7: Verify in Database

**Run this SQL:**
```sql
SELECT id, student_id, exam_session_id, type, details, timestamp
FROM events
ORDER BY timestamp DESC
LIMIT 10;
```

**Expected Result:**
```
id | student_id | exam_session_id | type             | details
---+------------+-----------------+------------------+---------------------
50 | 9          | 1               | KEY_COMBINATION  | Blocked: Ctrl+C
49 | 9          | 1               | WINDOW_FOCUS     | User returned...
48 | 9          | 1               | WINDOW_BLUR      | User focus left...
```

**✅ `exam_session_id` column should be `1`, NOT NULL!**

---

## 🔍 If Still NULL:

### Debug Step 1: Check Content Script Variable

**Add this to `content.js` in the logEvent function:**

```javascript
function logEvent(type, details) {
  if (!isMonitoring) {
    console.log('⚠️ Event ignored - monitoring not active:', type);
    return;
  }
  
  console.log('🔵 Logging event:', type, '|', details);
  console.log('📋 examSessionId variable value:', examSessionId);  // ← ADD THIS
  
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      type: type,
      details: details,
      examSessionId: examSessionId,  // ← This should not be null!
      timestamp: new Date().toISOString()
    }
  }, ...);
}
```

**Test Ctrl+C again, should see:**
```
📋 examSessionId variable value: 1
```

**If shows `null`:** Content script didn't load it from storage!

---

### Debug Step 2: Check Background Script

**Add this to `background.js` in logEventToBackend:**

```javascript
const formData = new FormData();
formData.append('studentId', studentInfo.studentId);
formData.append('type', eventData.type);

if (eventData.details) {
  formData.append('details', eventData.details);
}

if (eventData.examSessionId) {
  console.log('📤 Adding examSessionId to FormData:', eventData.examSessionId);  // ← ADD THIS
  formData.append('examSessionId', eventData.examSessionId);
} else {
  console.warn('⚠️ examSessionId is missing from eventData!');  // ← ADD THIS
}
```

**Test Ctrl+C again, check background console (chrome://extensions/ → Details → Inspect views service worker)**

**Should see:**
```
📤 Adding examSessionId to FormData: 1
```

**If you see the warning:** examSessionId is not in the message from content script!

---

### Debug Step 3: Check Backend Logs

**Look at the backend terminal where `mvn spring-boot:run` is running**

**Should see:**
```
Received event log request - studentId: 9, type: KEY_COMBINATION, details: Blocked: Ctrl+C, examSessionId: 1
```

**If examSessionId is null in the log:** Backend didn't receive it from extension!

---

## 📊 Complete Flow Diagram:

```
1. User enters examSessionId in popup: "1"
   ↓
2. Popup saves to chrome.storage.local: { examSessionId: "1" }
   ↓
3. Content script loads on startup: examSessionId = "1"
   ↓
4. User presses Ctrl+C
   ↓
5. Content script sends message with examSessionId: 1
   ↓
6. Background script receives message
   ↓
7. Background adds to FormData: examSessionId=1
   ↓
8. Backend receives POST /api/events/log
   ↓
9. Backend controller: if (examSessionId != null) { ... }
   ↓
10. Backend looks up ExamSession by ID: examSessionService.findById(1)
   ↓
11. Backend sets on Event: event.setExamSession(examSession)
   ↓
12. Backend saves Event: eventRepo.save(event)
   ↓
13. Database INSERT: exam_session_id = 1
   ↓
✅ SUCCESS! exam_session_id is saved!
```

**If ANY step fails, exam_session_id will be NULL!**

---

## 🎯 CHECKLIST:

- [ ] Backend is running (`mvn spring-boot:run`)
- [ ] Exam session exists in database (run INSERT SQL)
- [ ] Extension reloaded in chrome://extensions/
- [ ] Fresh JWT token obtained
- [ ] Student ID, Exam Session ID, Token saved in extension
- [ ] Monitoring started
- [ ] Console shows: `📋 Exam Session ID: 1` (not null)
- [ ] Ctrl+C triggers event
- [ ] Console shows: `✅ Event logged successfully`
- [ ] No 401 errors
- [ ] Database shows: `exam_session_id = 1` (not NULL)

---

## 📝 Files I Created for You:

1. **EXAM_SESSION_FIX.md** - Complete analysis
2. **diagnose-examsession.ps1** - Diagnostic script
3. **FIX_401_ERROR_NOW.md** - JWT token fix guide
4. **QUICK_401_FIX.md** - Browser console method

---

## 🚀 DO THIS NOW:

1. **Start backend:** `mvn spring-boot:run`
2. **Create exam session** in MySQL (run INSERT SQL above)
3. **Get fresh token** (run browser console code above)
4. **Reload extension**
5. **Save credentials** with Student ID, Exam Session ID, Token
6. **Start monitoring**
7. **Press Ctrl+C**
8. **Check database:** `SELECT * FROM events ORDER BY timestamp DESC LIMIT 1;`

**The `exam_session_id` column should NOT be NULL!** ✅

---

**Report back:** What do you see when you run the SQL query?
