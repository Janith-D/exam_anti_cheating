# Alert Endpoints Testing Guide

## Issue: 401 Unauthorized on `/api/alerts/active`

The endpoint requires **ADMIN role**, which means you need to:
1. Have a user with ADMIN role
2. Be logged in with that ADMIN user
3. Use the JWT token from that login

## Fixed Issues

1. **Type mismatch in AlertRepo** - Changed from `String` to `Enums.AlertStatus` and `Enums.AlertSeverity`
2. **AlertService** - Removed `.name()` calls to pass enum directly

---

## Step-by-Step Testing

### Step 1: Register an ADMIN User

First, you need an ADMIN user in your system:

```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=admin1" `
  -F "password=AdminPass123!" `
  -F "email=admin1@test.com" `
  -F "firstName=Admin" `
  -F "lastName=User" `
  -F "role=ADMIN" `
  -F "studentId=ADMIN001" `
  -F "image=@C:\path\to\image.jpg"
```

**Important:** Set `role=ADMIN` (not STUDENT)

---

### Step 2: Login as ADMIN

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=admin1" `
  -F "password=AdminPass123!" `
  -F "image=@C:\path\to\image.jpg"
```

**Response will include:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9...",
  "userName": "admin1",
  "role": "ADMIN",
  ...
}
```

**⚠️ IMPORTANT:** 
- Save the entire `token` value
- Verify `role` is "ADMIN" in the response

---

### Step 3: Test Alert Endpoints

Now you can test all alert endpoints with your ADMIN token.

#### 3.1: Get Active Alerts

**Postman:**
- Method: `GET`
- URL: `http://localhost:8080/api/alerts/active`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_TOKEN`

**cURL:**
```powershell
$adminToken = "YOUR_ADMIN_TOKEN_HERE"
curl.exe -X GET http://localhost:8080/api/alerts/active `
  -H "Authorization: Bearer $adminToken"
```

**Expected Response:**
```json
[
  {
    "id": 1,
    "severity": "HIGH",
    "message": "Excessive tab switching (3 in 5 min)",
    "status": "ACTIVE",
    "timestamp": "2025-10-12T10:30:00",
    ...
  }
]
```

**If empty `[]`:** No active alerts in last 24 hours. This is normal if no suspicious activity was detected.

---

#### 3.2: Get Alerts by Student

**Postman:**
- Method: `GET`
- URL: `http://localhost:8080/api/alerts/student/1`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_TOKEN`

**cURL:**
```powershell
curl.exe -X GET http://localhost:8080/api/alerts/student/1 `
  -H "Authorization: Bearer $adminToken"
```

---

#### 3.3: Get Alerts by Severity

**Postman:**
- Method: `GET`
- URL: `http://localhost:8080/api/alerts/severity/HIGH`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_TOKEN`

**cURL:**
```powershell
curl.exe -X GET http://localhost:8080/api/alerts/severity/HIGH `
  -H "Authorization: Bearer $adminToken"
```

**Valid severity values:**
- `LOW`
- `MEDIUM`
- `HIGH`
- `CRITICAL`

---

#### 3.4: Resolve Alert

**Postman:**
- Method: `PUT`
- URL: `http://localhost:8080/api/alerts/resolve/1`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_TOKEN`
  - `Content-Type: application/json`
- Body (raw JSON):
```json
{
  "resolvedBy": "admin1"
}
```

**cURL:**
```powershell
curl.exe -X PUT http://localhost:8080/api/alerts/resolve/1 `
  -H "Authorization: Bearer $adminToken" `
  -H "Content-Type: application/json" `
  -d "{\"resolvedBy\": \"admin1\"}"
```

---

## How to Create Test Alerts

Alerts are created automatically when suspicious events are detected. To generate test alerts:

### Method 1: Trigger Tab Switch Alert

Log 3+ tab switches within 5 minutes:

```powershell
$studentToken = "YOUR_STUDENT_TOKEN"

# Log tab switch 1
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=TAB_SWITCH" `
  -F "details=Switched to browser"

# Log tab switch 2
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=TAB_SWITCH" `
  -F "details=Switched to another tab"

# Log tab switch 3
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=TAB_SWITCH" `
  -F "details=Switched to email"
```

**This will trigger:** HIGH severity alert "Excessive tab switching"

---

### Method 2: Trigger Copy/Paste Alert

Log 3+ copy/paste events within 5 minutes:

```powershell
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=COPY"

curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=PASTE"

curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=COPY"
```

**This will trigger:** MEDIUM severity alert "Suspicious copy/paste Activity"

---

### Method 3: Trigger Right Click Alert

Log 6+ right clicks within 5 minutes:

```powershell
for ($i=1; $i -le 6; $i++) {
    curl.exe -X POST http://localhost:8080/api/events/log `
      -H "Authorization: Bearer $studentToken" `
      -F "studentId=1" `
      -F "type=RIGHT_CLICK" `
      -F "details=Right click $i"
}
```

**This will trigger:** LOW severity alert "Excessive right-clicks"

---

## Common Issues and Solutions

### Issue 1: 401 Unauthorized

**Possible Causes:**
1. Not using ADMIN token
2. Token expired
3. Token format wrong

**Solution:**
```powershell
# Check your token starts with "eyJ"
echo $adminToken

# Make sure role is ADMIN when you login
# Response should show: "role": "ADMIN"

# Verify Authorization header format
Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...
                    ↑ Space after Bearer!
```

---

### Issue 2: Empty Array `[]` Response

**Cause:** No alerts match the criteria

**For `/active`:**
- Only returns alerts from last 24 hours with ACTIVE status
- If no suspicious activity detected, no alerts exist

**Solution:**
- Create test alerts by triggering rules (see above)
- Or use `/student/{studentId}` to see all alerts for a student

---

### Issue 3: Cannot Register ADMIN User

**Cause:** Role validation or existing user

**Solution:**
- Make sure `role=ADMIN` (uppercase)
- Use unique username and email
- Check if user already exists in database

---

### Issue 4: Backend Error After Fix

**Cause:** Type mismatch needs backend restart

**Solution:**
```powershell
# Stop backend (Ctrl+C)
# Restart:
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

---

## Complete Test Flow

```powershell
# 1. Register ADMIN
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=testadmin" `
  -F "password=Pass123!" `
  -F "email=testadmin@test.com" `
  -F "firstName=Test" `
  -F "lastName=Admin" `
  -F "role=ADMIN" `
  -F "studentId=ADM001" `
  -F "image=@C:\path\to\image.jpg"

# 2. Login as ADMIN (copy the token)
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testadmin" `
  -F "password=Pass123!" `
  -F "image=@C:\path\to\image.jpg"

# 3. Set token
$adminToken = "PASTE_TOKEN_HERE"

# 4. Generate test alerts (login as student first)
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=existingStudent" `
  -F "password=studentPass" `
  -F "image=@C:\path\to\image.jpg"

$studentToken = "STUDENT_TOKEN"

# Trigger alerts
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH"
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH"
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH"

# 5. Check alerts as ADMIN
curl.exe -X GET http://localhost:8080/api/alerts/active -H "Authorization: Bearer $adminToken"
curl.exe -X GET http://localhost:8080/api/alerts/student/1 -H "Authorization: Bearer $adminToken"
curl.exe -X GET http://localhost:8080/api/alerts/severity/HIGH -H "Authorization: Bearer $adminToken"
```

---

## Postman Collection Structure

Create a folder "Alerts" with these requests:

1. **Get Active Alerts**
   - GET `/api/alerts/active`
   - Auth: Bearer Token (use `{{adminToken}}` variable)

2. **Get Alerts by Student**
   - GET `/api/alerts/student/{{studentId}}`
   - Auth: Bearer Token

3. **Get Alerts by Severity**
   - GET `/api/alerts/severity/{{severity}}`
   - Auth: Bearer Token

4. **Resolve Alert**
   - PUT `/api/alerts/resolve/{{alertId}}`
   - Auth: Bearer Token
   - Body: `{"resolvedBy": "{{adminUsername}}"}`

---

## Database Check

To verify alerts exist:

```sql
-- Check all alerts
SELECT * FROM alerts;

-- Check active alerts
SELECT * FROM alerts WHERE status = 'ACTIVE';

-- Check alerts by severity
SELECT * FROM alerts WHERE severity = 'HIGH';

-- Check alerts by student
SELECT * FROM alerts WHERE student_id = 1;
```

---

**After restarting the backend, create an ADMIN user and test with the ADMIN token!** 🎯
