# 🔴 FIX 401 ERROR - Complete Solution

## ❌ Your Error:
```
❌ Failed to log event: WINDOW_BLUR HTTP 401: Authentication failed
❌ Failed to log event: TAB_SWITCH HTTP 401: Authentication failed  
❌ Failed to log event: SNAPSHOT HTTP 401: Authentication failed
Error: HTTP 401: Authentication failed. Please save your credentials again
```

---

## 🎯 Root Cause:

**The JWT token is either:**
1. **Expired** (tokens expire after 24 hours)
2. **Invalid** (backend restarted and secret key changed)
3. **Not saved** (extension storage empty)

---

## ✅ SOLUTION - Get Fresh Token

### Option 1: Via Backend Direct (Recommended)

**Step 1: Check if backend is running**
```powershell
# Test if backend responds
Invoke-RestMethod -Uri http://localhost:8080/actuator/health
```

**Expected:** `{ "status": "UP" }`

**If error:** Start the backend first!

---

**Step 2: Register or login as a student**

There are TWO ways to get a token:

### Method A: Register New Student (If no student exists)

```powershell
# Register a new student
$registerBody = @{
    username = "student1"
    email = "student1@example.com"
    password = "student123"
    role = "STUDENT"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/api/auth/register `
    -Method Post `
    -Body $registerBody `
    -ContentType "application/json"
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzdHVkZW50MSIsInJvbGVzIjpbIlNUVURFTlQiXSwiaWF0IjoxNzI4OTM2MDAwLCJleHAiOjE3MjkwMjI0MDB9.abc123...",
  "userId": 1,
  "username": "student1",
  "email": "student1@example.com",
  "roles": ["STUDENT"]
}
```

**Copy the `token` value!**

---

### Method B: Login with Existing Student

If you already registered a student (like student ID 7 or 8), use their credentials:

```powershell
# Login with existing student
$loginBody = @{
    username = "student1"  # or whatever username you used
    password = "student123"
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/api/auth/login `
    -Method Post `
    -Body $loginBody `
    -ContentType "application/json"
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userId": 7,
  "username": "student1",
  "email": "student1@example.com",
  "roles": ["STUDENT"]
}
```

**Copy the `token` value!**

---

### Option 2: Check Database for Existing Students

```sql
-- Run this in MySQL
SELECT student_id, user_name, email, role 
FROM students 
WHERE role = 'STUDENT';
```

**If you see students, use their username to login!**

Example:
```
student_id | user_name | email              | role
-----------+-----------+--------------------+---------
7          | john_doe  | john@example.com   | STUDENT
8          | jane_doe  | jane@example.com   | STUDENT
```

Then login:
```powershell
$loginBody = @{
    username = "john_doe"  # Use the user_name from database
    password = "password123"  # The password they registered with
} | ConvertTo-Json

Invoke-RestMethod -Uri http://localhost:8080/api/auth/login `
    -Method Post `
    -Body $loginBody `
    -ContentType "application/json"
```

---

## 🔧 SAVE TOKEN IN EXTENSION

### Step 1: Copy the Token
After running one of the commands above, you'll get a response like:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzdHVkZW50MSIsInJvbGVzIjpbIlNUVURFTlQiXSwiaWF0IjoxNzI4OTM2MDAwLCJleHAiOjE3MjkwMjI0MDB9.VeryLongStringHere..."
}
```

**Copy ONLY the token value** (everything after `"token": "` and before the closing `"`)

---

### Step 2: Save in Extension

1. **Click the extension icon** in Chrome toolbar
2. **Enter credentials:**
   - **Student ID:** The `userId` from the response (e.g., `7`)
   - **Exam Session ID:** `1` (or whatever exam session you're testing)
   - **JWT Token:** Paste the long token string you copied
3. **Click "Save Credentials"**
4. You should see: ✅ "Credentials saved successfully!"

---

### Step 3: Start Monitoring

1. **Still in the extension popup**
2. **Click "Start Monitoring"**
3. You should see: ✅ "Monitoring started successfully!"

---

### Step 4: Verify No More 401 Errors

1. **Open Console** (F12 → Console tab)
2. **Press Ctrl+C** on the page
3. **Check console** - Should see:
   ```
   🔵 Logging event: KEY_COMBINATION | Blocked: Ctrl+C
   ✅ Event logged successfully: KEY_COMBINATION
   📊 Event count updated: 1
   ```

**NO MORE 401 ERRORS!** ✅

---

## 🔍 DEBUGGING

### If you still get 401 errors:

**Check 1: Is backend running?**
```powershell
Invoke-RestMethod -Uri http://localhost:8080/actuator/health
```
Should return `{ "status": "UP" }`

---

**Check 2: Is token saved in extension?**
Open console and run:
```javascript
chrome.storage.local.get(['jwtToken', 'studentInfo'], console.log)
```

**Expected:**
```javascript
{
  jwtToken: "eyJhbGciOiJIUzUxMiJ9...",
  studentInfo: {
    studentId: "7",
    userName: "student1",
    role: "STUDENT"
  }
}
```

**If empty:** Token not saved! Save it again in popup.

---

**Check 3: Is token valid?**

Test the token manually:
```powershell
$token = "YOUR_TOKEN_HERE"  # Paste your token
$headers = @{
    "Authorization" = "Bearer $token"
}

# Try to get events (should work if token is valid)
Invoke-RestMethod -Uri http://localhost:8080/api/events/log `
    -Method Post `
    -Headers $headers `
    -Body (@{
        studentId = "7"
        type = "TEST"
        details = "Testing token"
    } | ConvertTo-Json) `
    -ContentType "application/json"
```

**If 401:** Token is invalid! Get a fresh one.
**If 200:** Token is valid! Problem is in extension.

---

**Check 4: Token format correct?**

JWT tokens have 3 parts separated by dots:
```
eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJzdHVkZW50MSI...ABC123
^^^^^^^^^^^^^^^^^^^^^  ^^^^^^^^^^^^^^^^^^^^^^^^^^  ^^^^^^
      Header                    Payload            Signature
```

**Make sure:**
- No extra spaces
- No line breaks
- Starts with `eyJ`
- Has exactly 2 dots

---

## 🎯 QUICK FIX SUMMARY

```powershell
# 1. Register new student (or login with existing)
$body = @{
    username = "student1"
    email = "student1@example.com"
    password = "student123"
    role = "STUDENT"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri http://localhost:8080/api/auth/register `
    -Method Post `
    -Body $body `
    -ContentType "application/json"

# 2. Show token
Write-Host "Your JWT Token:"
Write-Host $response.token

# 3. Show student ID
Write-Host "Student ID: $($response.userId)"

# 4. Instructions
Write-Host ""
Write-Host "Now:"
Write-Host "1. Copy the token above"
Write-Host "2. Open extension popup"
Write-Host "3. Enter Student ID: $($response.userId)"
Write-Host "4. Enter Exam Session ID: 1"
Write-Host "5. Paste the token"
Write-Host "6. Click Save Credentials"
Write-Host "7. Click Start Monitoring"
```

---

## 📊 Why This Happens

**JWT Token Lifecycle:**
```
1. User registers/logs in → Backend generates token
2. Token expires after 24 hours (or backend restarts)
3. Extension tries to use expired token → 401 Error
4. Solution: Get fresh token!
```

**Backend Secret Key:**
- Tokens are signed with a secret key
- If backend restarts, secret key might change
- Old tokens become invalid
- Solution: Get fresh token!

---

## ✅ After Fixing:

**You should see in console:**
```
✅ Monitoring started in content script
🔵 Logging event: WINDOW_BLUR | User focus left the exam window
📨 Received logEvent request: WINDOW_BLUR
✅ Event logged successfully to backend: WINDOW_BLUR
📊 Event count updated: 1
```

**NO MORE 401 ERRORS!** 🎉

---

## 🎬 Do This Right Now:

1. **Open PowerShell**
2. **Run this:**
   ```powershell
   cd d:\PROJECT\Exam-Anti-Cheating
   
   # Register new student
   $body = '{"username":"teststudent","email":"test@example.com","password":"test123","role":"STUDENT"}' 
   $response = Invoke-RestMethod -Uri http://localhost:8080/api/auth/register -Method Post -Body $body -ContentType "application/json"
   
   # Show token
   Write-Host "`nYour Token:"
   Write-Host $response.token -ForegroundColor Yellow
   Write-Host "`nStudent ID: $($response.userId)" -ForegroundColor Green
   Write-Host "`nCopy the token above and save it in the extension!" -ForegroundColor Cyan
   ```

3. **Copy the token**
4. **Open extension popup**
5. **Paste token + Student ID + Exam Session ID**
6. **Click Save Credentials**
7. **Click Start Monitoring**
8. **Test Ctrl+C - Should work without 401!**

---

**PROBLEM SOLVED!** ✅
