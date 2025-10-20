# 🚀 BACKEND START GUIDE

## Your Issue:
> "postman doestnot working how to cheack port and postman show this Error: connect ECONNREFUSED 127.0.0.1:8080"

**Translation:** Backend is NOT running on port 8080. Postman can't connect.

---

## ✅ FIXES APPLIED:

### Fix 1: Fixed Typo in QuestionRepo.java
**Problem:** `findBuTestId` (typo!) → Should be `findByTestId`
**Fixed:** Removed duplicate method with typo

###Fix 2: Fixed Duplicate Endpoint Mapping
**Problem:** Both HealthController and TestController used `/api/test`
**Fixed:** Changed HealthController to use `/api/health-test`

---

## 🎯 BACKEND IS NOW STARTING!

**Command running:**
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

**Wait for this message:**
```
Started AntiCheatingBackendApplication in X.XXX seconds (process running for X.XXX)
```

---

## ✅ HOW TO CHECK IF BACKEND IS RUNNING:

### Method 1: Check Port
```powershell
netstat -ano | findstr :8080
```

**Expected output:**
```
TCP    0.0.0.0:8080         0.0.0.0:0              LISTENING       12345
TCP    [::]:8080            [::]:0                 LISTENING       12345
```

**If empty:** Backend is NOT running!

---

### Method 2: Health Check in Browser
Open browser and go to:
```
http://localhost:8080/health
```

**Expected response:**
```json
{
  "status": "UP",
  "message": "Anti-Cheating Backend is running",
  "timestamp": 1729010000000
}
```

---

### Method 3: PowerShell Health Check
```powershell
Invoke-RestMethod -Uri http://localhost:8080/health
```

**Expected output:**
```
status    : UP
message   : Anti-Cheating Backend is running
timestamp : 1729010000000
```

---

## 📋 AVAILABLE ENDPOINTS:

Once backend is running, test these:

### 1. Health Check (No auth required)
```
GET http://localhost:8080/health
```

### 2. API Test (No auth required)
```
GET http://localhost:8080/api/health-test
```

### 3. Register Student
```
POST http://localhost:8080/api/auth/register
Content-Type: application/json

{
  "username": "student1",
  "email": "student1@test.com",
  "password": "test123",
  "role": "STUDENT"
}
```

### 4. Login
```
POST http://localhost:8080/api/auth/login
Content-Type: application/json

{
  "username": "student1",
  "password": "test123"
}
```

### 5. Log Event (Requires JWT)
```
POST http://localhost:8080/api/events/log
Authorization: Bearer YOUR_JWT_TOKEN_HERE
Content-Type: multipart/form-data

studentId=1
type=COPY
details=Test event
examSessionId=1
```

---

## 🐛 IF BACKEND FAILS TO START:

### Check Terminal Output

Look for these errors:

#### Error 1: Port Already in Use
```
Web server failed to start. Port 8080 was already in use.
```

**Solution:** Kill the process using port 8080
```powershell
netstat -ano | findstr :8080
# Note the PID (last number)
taskkill /PID <PID> /F
```

#### Error 2: MySQL Connection Failed
```
Communications link failure
The last packet sent successfully to the server was 0 milliseconds ago.
```

**Solution:** Start MySQL service
```powershell
# Check MySQL status
Get-Service -Name MySQL*

# Start MySQL
Start-Service -Name MySQL80  # or your MySQL service name
```

#### Error 3: Database Doesn't Exist
```
Unknown database 'anti_cheating_db'
```

**Solution:** MySQL will create it automatically if using the correct URL:
```
jdbc:mysql://localhost:3306/anti_cheating_db?createDatabaseIfNotExist=true
```

This is already in `application.properties`!

---

## ✅ POSTMAN TESTING GUIDE:

### Step 1: Test Health Endpoint

1. Open Postman
2. Create new request:
   - Method: **GET**
   - URL: `http://localhost:8080/health`
3. Click **Send**

**Expected Response (200 OK):**
```json
{
    "status": "UP",
    "message": "Anti-Cheating Backend is running",
    "timestamp": 1729010000000
}
```

---

### Step 2: Register a Student

1. Create new request:
   - Method: **POST**
   - URL: `http://localhost:8080/api/auth/register`
   - Headers:
     - Key: `Content-Type`
     - Value: `application/json`
   - Body → raw → JSON:
     ```json
     {
       "username": "teststudent",
       "email": "test@test.com",
       "password": "test123",
       "role": "STUDENT"
     }
     ```
2. Click **Send**

**Expected Response (200 OK):**
```json
{
    "token": "eyJhbGciOiJIUzUxMiJ9...",
    "userId": 1,
    "username": "teststudent",
    "email": "test@test.com",
    "roles": ["STUDENT"]
}
```

**Copy the `token` value!**

---

### Step 3: Log an Event

1. Create new request:
   - Method: **POST**
   - URL: `http://localhost:8080/api/events/log`
   - Headers:
     - Key: `Authorization`
     - Value: `Bearer eyJhbGciOiJIUzUxMiJ9...` (paste your token)
   - Body → form-data:
     - Key: `studentId` → Value: `1`
     - Key: `type` → Value: `COPY`
     - Key: `details` → Value: `Test copy event`
     - Key: `examSessionId` → Value: `1`
2. Click **Send**

**Expected Response (200 OK):**
```json
{
    "message": "Event logged",
    "eventId": 1
}
```

---

## 🎯 QUICK TEST SCRIPT:

Save this as `test-backend.ps1`:

```powershell
Write-Host "Testing backend..." -ForegroundColor Cyan

# Test 1: Health check
Write-Host "`n1. Testing health endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/health"
    Write-Host "   Status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: Backend not running!" -ForegroundColor Red
    exit 1
}

# Test 2: Register
Write-Host "`n2. Registering test student..." -ForegroundColor Yellow
$body = @{
    username = "test_" + (Get-Date -Format "HHmmss")
    email = "test@test.com"
    password = "test123"
    role = "STUDENT"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
        -Method Post -Body $body -ContentType "application/json"
    Write-Host "   Student ID: $($response.userId)" -ForegroundColor Green
    Write-Host "   Token: $($response.token.Substring(0,50))..." -ForegroundColor Green
    
    Write-Host "`nBackend is working!" -ForegroundColor Green
} catch {
    Write-Host "   FAILED: $_" -ForegroundColor Red
}
```

**Run it:**
```powershell
.\test-backend.ps1
```

---

## 📊 SUMMARY:

| Issue | Status | Solution |
|-------|--------|----------|
| Port 8080 not listening | ❌ Backend not running | Start with `.\mvnw.cmd spring-boot:run` |
| Postman ECONNREFUSED | ❌ Backend not running | Wait for "Started AntiCheatingBackendApplication" |
| Typo in QuestionRepo | ✅ FIXED | Changed `findBuTestId` → Removed duplicate |
| Duplicate endpoint mapping | ✅ FIXED | Changed `/api/test` → `/api/health-test` |

---

## 🚀 NEXT STEPS:

1. **Wait for backend to finish starting** (look for "Started AntiCheatingBackendApplication")
2. **Test health endpoint**: `http://localhost:8080/health`
3. **Test in Postman**: Register → Get token → Log event
4. **Configure extension**: Use the JWT token in extension popup
5. **Start monitoring**: Extension should now work without 401 errors!

---

**Backend should be starting now! Check the terminal for "Started" message!**
