# 🔧 Extension Issues - Troubleshooting Guide

**Date:** October 13, 2025  
**Issues:** Extension closing automatically + Backend 400 errors

---

## ✅ Issues Fixed

### Issue 1: Popup Closes Automatically ✅ FIXED

**Problem:**  
When clicking "Start Monitoring", the popup closes immediately and you can't see if it worked.

**Why it happens:**  
This is normal Chrome extension behavior - popups close automatically when they lose focus.

**Solution:**  
✅ Added credential check before starting monitoring
✅ Added notification to all tabs to start monitoring  
✅ Monitoring state is saved in chrome.storage (persists even when popup closes)
✅ You can reopen popup anytime to check status

**How to verify it's working:**
1. Click "Start Monitoring"
2. Popup may close (this is normal)
3. Click extension icon again
4. Status should show "Monitoring Active" 🟢
5. Event counter should increase as you perform actions

---

### Issue 2: Backend 400 Bad Request ✅ IMPROVED

**Possible causes:**
1. Invalid EventType enum value
2. Missing JWT token or invalid token
3. Student ID not found in database
4. Missing required parameters

**Solutions applied:**
✅ Added detailed logging in EventController
✅ Added better error messages showing exactly what's wrong
✅ Added validation for EventType before processing
✅ Added better error handling in extension background.js
✅ Console logs now show exact error from backend

---

## 🧪 Step-by-Step Testing

### Step 1: Check Backend is Running

```powershell
# Navigate to backend folder
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend

# Start backend
.\mvnw.cmd spring-boot:run
```

**Wait for:**
```
Started AntiCheatingBackendApplication in X.XXX seconds
```

---

### Step 2: Create/Login Student User

```powershell
# Register a student (if not already done)
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=testStudent" `
  -F "password=Test123!" `
  -F "email=test@student.com" `
  -F "firstName=Test" `
  -F "lastName=Student" `
  -F "role=STUDENT" `
  -F "studentId=TEST001" `
  -F "image=@C:\path\to\any\photo.jpg"
```

**Expected response:**
```json
{
  "message": "User registered successfully",
  "studentId": 1,
  "enrollmentId": 1,
  "userName": "testStudent"
}
```

**IMPORTANT: Note the `studentId` value (e.g., 1)**

---

```powershell
# Login to get JWT token
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testStudent" `
  -F "password=Test123!" `
  -F "image=@C:\path\to\same\photo.jpg"
```

**Expected response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0U3R1ZGVudC...",
  "type": "Bearer",
  "userName": "testStudent",
  "email": "test@student.com",
  "role": "STUDENT",
  "studentId": 1
}
```

**IMPORTANT: Copy the entire `token` value**

---

### Step 3: Configure Extension

1. **Reload Extension:**
   - Open: `chrome://extensions/`
   - Find "Anti-Cheating Proctor Extension"
   - Click the reload icon (🔄)

2. **Open Extension Popup:**
   - Click the extension icon in Chrome toolbar

3. **Enter Credentials:**
   - **Student ID:** Enter the `studentId` from Step 2 (e.g., `1`)
   - **JWT Token:** Paste the entire `token` value from Step 2
   - Click "Save Credentials"

4. **Verify:**
   - Should see "Credentials saved successfully!" message
   - Student info section should appear showing your name and ID

---

### Step 4: Start Monitoring

1. **Click "Start Monitoring"** button

2. **Popup may close** - This is normal!

3. **Reopen popup** by clicking extension icon again

4. **Verify status:**
   - Status should show: "Monitoring Active" 🟢
   - Event Count should show: 0
   - Start button should be disabled
   - Stop button should be enabled

---

### Step 5: Test Event Logging

Open the **Chrome DevTools Console** to see logs:
- Press `F12` or `Ctrl+Shift+I`
- Click "Console" tab
- Keep this open while testing

**Now try these actions:**

#### Test 1: Copy Text
1. Select any text on the page
2. Press `Ctrl+C`
3. **Check console:** Should see "Event logged: COPY"
4. **Check backend logs:** Should see "Logged event: X, type: COPY"

#### Test 2: Paste Text
1. Press `Ctrl+V`
2. **Check console:** Should see "Event logged: PASTE"
3. **Check backend logs:** Should see "Logged event: X, type: PASTE"

#### Test 3: Switch Tab
1. Open another tab or click another tab
2. **Check console:** Should see "Event logged: TAB_SWITCH"
3. **Check backend logs:** Should see "Logged event: X, type: TAB_SWITCH"

#### Test 4: Right Click
1. Right-click anywhere on the page
2. **Check console:** Should see "Event logged: RIGHT_CLICK"
3. **Check backend logs:** Should see "Logged event: X, type: RIGHT_CLICK"

---

### Step 6: Verify Events in Extension

1. Click extension icon to open popup
2. **Event Count should have increased!**
3. Example: If you did 4 tests, it should show "4" or more

---

### Step 7: Verify Events in Backend

```powershell
# Get admin token first (login as admin)
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=adminUser" `
  -F "password=Admin123!" `
  -F "image=@C:\path\to\photo.jpg"

# Copy the admin token, then:
$adminToken = "PASTE_ADMIN_TOKEN_HERE"

# Get all events for student ID 1
curl.exe -X GET "http://localhost:8080/api/events/student/1/all" `
  -H "Authorization: Bearer $adminToken"
```

**Expected response:**
```json
{
  "studentId": 1,
  "eventCount": 4,
  "events": [
    {
      "id": 1,
      "type": "COPY",
      "details": "Copied text: \"example...\"",
      "timestamp": "2025-10-13T14:30:00",
      "student": {
        "id": 1,
        "userName": "testStudent"
      }
    },
    {
      "id": 2,
      "type": "PASTE",
      "details": "Pasted text into exam page",
      "timestamp": "2025-10-13T14:30:05"
    },
    ...
  ]
}
```

---

## 🐛 Still Getting 400 Error?

### Check Browser Console for Exact Error

1. Open Chrome DevTools (`F12`)
2. Go to "Console" tab
3. Look for red error messages
4. The error will show exactly what's wrong

**Common errors:**

#### Error 1: "No JWT token found"
```
Error in logEventToBackend: No JWT token found. Please login first.
```

**Fix:**
- Click extension icon
- Enter Student ID and JWT Token
- Click "Save Credentials"

---

#### Error 2: "Student not found"
```
HTTP 400: {"error":"Student not found: 1"}
```

**Fix:**
- The Student ID you entered doesn't exist in database
- Check database: `SELECT * FROM students;`
- Use correct student ID from your database

---

#### Error 3: "Invalid event type"
```
HTTP 400: {"error":"Invalid event type: SOME_TYPE"}
```

**Fix:**
- The event type sent doesn't match backend enum
- Valid types: COPY, PASTE, TAB_SWITCH, WINDOW_BLUR, WINDOW_FOCUS, SNAPSHOT, RIGHT_CLICK, KEY_COMBINATION, FULLSCREEN_EXIT, BROWSER_DEVTOOLS, MULTIPLE_MONITORS, SUSPICIOUS_ACTIVITY

---

#### Error 4: Token expired or invalid
```
HTTP 401: Unauthorized
```

**Fix:**
- JWT token has expired (24 hour expiration)
- Login again to get fresh token
- Save new token in extension

---

### Check Backend Console for Detailed Logs

The backend now logs detailed information:

**When event received:**
```
INFO: Received event log request - studentId: 1, type: COPY, details: Copied text..., examSessionId: null
```

**When event saved:**
```
INFO: Logged event: 1, type: COPY
```

**When error occurs:**
```
SEVERE: Error logging event: Student not found: 1
```

Or:
```
WARNING: Invalid event type received: WRONG_TYPE. Valid types: [COPY, PASTE, TAB_SWITCH, ...]
```

---

## 🔍 Debug Checklist

Run through this checklist:

- [ ] Backend is running on port 8080
- [ ] You can access: http://localhost:8080/health
- [ ] Student user exists in database
- [ ] JWT token is valid (not expired)
- [ ] Extension is loaded in Chrome
- [ ] Credentials saved in extension (Student ID + Token)
- [ ] Monitoring status is "Active" 🟢
- [ ] Browser console is open to see errors
- [ ] Backend console is open to see logs

---

## 📊 Testing Event Types

All event types that should work:

| Event Type | How to Trigger | Expected in Console |
|------------|---------------|---------------------|
| **COPY** | Ctrl+C | "Event logged: COPY" |
| **PASTE** | Ctrl+V | "Event logged: PASTE" |
| **TAB_SWITCH** | Switch tabs | "Event logged: TAB_SWITCH" |
| **WINDOW_BLUR** | Click outside browser | "Event logged: WINDOW_BLUR" |
| **WINDOW_FOCUS** | Click back in browser | "Event logged: WINDOW_FOCUS" |
| **RIGHT_CLICK** | Right-click mouse | "Event logged: RIGHT_CLICK" |
| **KEY_COMBINATION** | Ctrl+F, Ctrl+H, etc. | "Event logged: KEY_COMBINATION" |
| **BROWSER_DEVTOOLS** | Press F12 | "Event logged: BROWSER_DEVTOOLS" |
| **FULLSCREEN_EXIT** | Exit fullscreen (F11) | "Event logged: FULLSCREEN_EXIT" |
| **SNAPSHOT** | Auto every 30 sec | "Event logged: SNAPSHOT" |

---

## 🎯 Manual API Test

Test the API directly to isolate the issue:

```powershell
# Set your values
$token = "YOUR_JWT_TOKEN_HERE"
$studentId = 1

# Test event logging directly
curl.exe -X POST "http://localhost:8080/api/events/log" `
  -H "Authorization: Bearer $token" `
  -F "studentId=$studentId" `
  -F "type=COPY" `
  -F "details=Manual test from PowerShell"
```

**Expected response:**
```json
{
  "message": "Event logged",
  "eventId": 1
}
```

**If this works:** Problem is in extension  
**If this fails:** Problem is in backend (check logs for exact error)

---

## 💡 Quick Fixes

### Fix 1: Reset Extension State

```javascript
// Open Chrome DevTools Console (F12)
// Paste this code to reset extension:
chrome.storage.local.clear(() => {
  console.log('Extension storage cleared');
});

// Then reconfigure extension with credentials
```

### Fix 2: Check Extension Permissions

1. Open `chrome://extensions/`
2. Find "Anti-Cheating Proctor Extension"
3. Click "Details"
4. Check "Site access": Should be "On all sites"
5. Check permissions are granted

### Fix 3: Reload Everything

1. Stop backend (Ctrl+C)
2. Reload extension (`chrome://extensions/` → reload icon)
3. Start backend again
4. Configure extension again
5. Test

---

## 📝 Summary

**What was fixed:**
1. ✅ Added credential check before starting monitoring
2. ✅ Added detailed error logging in backend
3. ✅ Added better error messages
4. ✅ Added console logging in extension
5. ✅ Improved error handling

**What to check if still not working:**
1. Browser console for exact error
2. Backend console for exact error
3. Student ID exists in database
4. JWT token is valid
5. Extension has correct permissions

**Next steps if issues persist:**
1. Share the exact error from browser console
2. Share the exact error from backend console
3. Verify student ID exists in database
4. Verify JWT token is valid and not expired

---

**Status:** Extension should now work correctly with detailed error messages to help diagnose any remaining issues! 🎉
