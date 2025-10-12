# ✅ Pre-Production Verification Checklist

**Run through this checklist before moving to next development phase**

---

## 1️⃣ Backend Server Health

```powershell
# Start backend
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

### ✅ Verify Startup Success:
- [ ] No compilation errors
- [ ] Server starts on port 8080
- [ ] MySQL connection successful
- [ ] Hibernate schema created/updated
- [ ] No JWT secret errors in logs
- [ ] All beans initialized successfully

**Expected Log Output:**
```
Started AntiCheatingBackendApplication in X.XXX seconds
```

---

## 2️⃣ Database Verification

```powershell
# Connect to MySQL
mysql -u root -p
```

```sql
USE anti_cheating_db;

-- Verify tables exist
SHOW TABLES;
```

### ✅ Expected Tables:
- [ ] `students`
- [ ] `events`
- [ ] `alerts`
- [ ] `enrollments`
- [ ] `exam_sessions`

```sql
-- Check table structure
DESCRIBE students;
DESCRIBE events;
DESCRIBE alerts;

-- Verify empty or with test data
SELECT COUNT(*) FROM students;
SELECT COUNT(*) FROM events;
SELECT COUNT(*) FROM alerts;
```

---

## 3️⃣ Authentication System Test

### Test 1: Register STUDENT User

```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=teststudent" `
  -F "password=Student123!" `
  -F "email=student@test.com" `
  -F "firstName=Test" `
  -F "lastName=Student" `
  -F "role=STUDENT" `
  -F "studentId=STU001" `
  -F "image=@C:\path\to\your\photo.jpg"
```

### ✅ Expected Response:
```json
{
  "message": "User registered successfully",
  "studentId": 1,
  "enrollmentId": 1,
  "userName": "teststudent"
}
```

- [ ] Status 200 OK
- [ ] User created in database
- [ ] Enrollment record created
- [ ] No errors in logs

---

### Test 2: Register ADMIN User

```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=testadmin" `
  -F "password=Admin123!" `
  -F "email=admin@test.com" `
  -F "firstName=Test" `
  -F "lastName=Admin" `
  -F "role=ADMIN" `
  -F "studentId=ADM001" `
  -F "image=@C:\path\to\your\photo.jpg"
```

- [ ] Status 200 OK
- [ ] Admin user created
- [ ] Role set correctly to ADMIN

---

### Test 3: Login as STUDENT

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=teststudent" `
  -F "password=Student123!" `
  -F "image=@C:\path\to\your\photo.jpg"
```

### ✅ Expected Response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0c3R1ZGVudCIsImlhdCI6MTY5NzA...",
  "type": "Bearer",
  "userName": "teststudent",
  "email": "student@test.com",
  "role": "STUDENT",
  "studentId": 1
}
```

- [ ] Status 200 OK
- [ ] JWT token returned
- [ ] Token starts with "eyJ"
- [ ] Role is "STUDENT"
- [ ] Copy token for next tests

**Save your token:**
```powershell
$studentToken = "PASTE_YOUR_TOKEN_HERE"
```

---

### Test 4: Login as ADMIN

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testadmin" `
  -F "password=Admin123!" `
  -F "image=@C:\path\to\your\photo.jpg"
```

- [ ] Status 200 OK
- [ ] JWT token returned
- [ ] Role is "ADMIN"

**Save admin token:**
```powershell
$adminToken = "PASTE_YOUR_ADMIN_TOKEN_HERE"
```

---

## 4️⃣ Event Logging System Test

### Test 1: Log TAB_SWITCH Event

```powershell
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $studentToken" `
  -F "studentId=1" `
  -F "type=TAB_SWITCH" `
  -F "details=Switched to browser"
```

### ✅ Expected Response:
```json
{
  "message": "Event logged",
  "eventId": 1
}
```

- [ ] Status 200 OK
- [ ] Event ID returned
- [ ] Event saved to database
- [ ] No alert yet (need 3+ events)

---

### Test 2: Trigger TAB_SWITCH Alert (log 3 times)

```powershell
# Event 1
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH" -F "details=Switch 1"

# Event 2
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH" -F "details=Switch 2"

# Event 3 (should trigger alert)
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=TAB_SWITCH" -F "details=Switch 3"
```

- [ ] All 3 events logged successfully
- [ ] Check logs for "Excessive tab switching" alert creation
- [ ] Alert should be in database

**Verify in database:**
```sql
SELECT * FROM events WHERE student_id = 1;
SELECT * FROM alerts WHERE student_id = 1;
```

---

### Test 3: Retrieve Events (as ADMIN)

```powershell
# Get all events for student 1
curl.exe -X GET http://localhost:8080/api/events/student/1/all `
  -H "Authorization: Bearer $adminToken"
```

### ✅ Expected Response:
```json
{
  "studentId": 1,
  "eventCount": 3,
  "events": [
    {
      "id": 1,
      "type": "TAB_SWITCH",
      "details": "Switch 1",
      "timestamp": "2025-10-12T...",
      ...
    },
    ...
  ]
}
```

- [ ] Status 200 OK
- [ ] All 3 events returned
- [ ] Event details correct

---

## 5️⃣ Alert System Test

### Test 1: Get Active Alerts (as ADMIN)

```powershell
curl.exe -X GET http://localhost:8080/api/alerts/active `
  -H "Authorization: Bearer $adminToken"
```

### ✅ Expected Response:
```json
[
  {
    "id": 1,
    "severity": "HIGH",
    "message": "Excessive tab switching (3 in 5 min)",
    "status": "ACTIVE",
    "timestamp": "2025-10-12T...",
    "student": {
      "id": 1,
      "userName": "teststudent"
    }
  }
]
```

- [ ] Status 200 OK
- [ ] Alert returned
- [ ] Severity is HIGH
- [ ] Status is ACTIVE

---

### Test 2: Get Alerts by Student

```powershell
curl.exe -X GET http://localhost:8080/api/alerts/student/1 `
  -H "Authorization: Bearer $adminToken"
```

- [ ] Status 200 OK
- [ ] Student's alerts returned

---

### Test 3: Get Alerts by Severity

```powershell
curl.exe -X GET http://localhost:8080/api/alerts/severity/HIGH `
  -H "Authorization: Bearer $adminToken"
```

- [ ] Status 200 OK
- [ ] Only HIGH severity alerts returned

---

### Test 4: Resolve Alert

```powershell
curl.exe -X PUT http://localhost:8080/api/alerts/resolve/1 `
  -H "Authorization: Bearer $adminToken" `
  -H "Content-Type: application/json" `
  -d "{\"resolvedBy\": \"testadmin\"}"
```

### ✅ Expected Response:
```json
{
  "message": "Alert resolved",
  "alertId": 1
}
```

- [ ] Status 200 OK
- [ ] Alert marked as RESOLVED
- [ ] resolvedBy set to "testadmin"

**Verify:**
```sql
SELECT * FROM alerts WHERE id = 1;
-- status should be 'RESOLVED'
-- resolved_by should be 'testadmin'
-- resolved_at should have timestamp
```

---

## 6️⃣ Authorization Test

### Test 1: Student Cannot Access Admin Endpoints

```powershell
# Try to get all events as STUDENT (should fail)
curl.exe -X GET http://localhost:8080/api/events/student/1/all `
  -H "Authorization: Bearer $studentToken"

# Try to get active alerts as STUDENT (should fail)
curl.exe -X GET http://localhost:8080/api/alerts/active `
  -H "Authorization: Bearer $studentToken"
```

- [ ] ✅ Both should return 403 Forbidden
- [ ] This proves role-based access control is working

---

### Test 2: Endpoints Without Token Fail

```powershell
# Try without Authorization header (should fail)
curl.exe -X GET http://localhost:8080/api/alerts/active
```

- [ ] ✅ Should return 401 Unauthorized
- [ ] This proves JWT authentication is required

---

## 7️⃣ Additional Cheating Detection Rules Test

### Test 1: Trigger COPY/PASTE Alert

```powershell
# Log 3+ copy/paste events
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=COPY"
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=PASTE"
curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=COPY"
```

- [ ] Alert created with MEDIUM severity
- [ ] Message: "Suspicious copy/paste Activity"

---

### Test 2: Trigger RIGHT_CLICK Alert

```powershell
# Log 6+ right-click events
for ($i=1; $i -le 6; $i++) {
    curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=RIGHT_CLICK" -F "details=Right click $i"
}
```

- [ ] Alert created with LOW severity
- [ ] Message: "Excessive right-clicks"

---

## 8️⃣ Database Integrity Check

```sql
-- Check referential integrity
SELECT 
    e.id, e.type, e.timestamp,
    s.userName, s.role
FROM events e
JOIN students s ON e.student_id = s.id;

-- Check alerts are linked to events and students
SELECT 
    a.id, a.severity, a.message, a.status,
    s.userName,
    e.type as event_type
FROM alerts a
JOIN students s ON a.student_id = s.id
LEFT JOIN events e ON a.event_id = e.id;

-- Verify enrollment data
SELECT 
    en.id, en.enrollment_id, en.status,
    s.userName, s.role
FROM enrollments en
JOIN students s ON en.student_id = s.id;
```

- [ ] All joins work correctly
- [ ] No orphaned records
- [ ] Foreign keys intact

---

## 9️⃣ ML Service Test (Optional)

**Only if you plan to enable face recognition**

### Start ML Service:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\ml-service
.venv\Scripts\Activate.ps1
python src/api.py
```

### Test ML Health:
```powershell
curl.exe http://localhost:5000/health
```

- [ ] ML service running on port 5000
- [ ] Health endpoint returns OK

### Enable ML in Backend:
```properties
# In application.properties
ml.service.enabled=true
```

- [ ] Restart backend
- [ ] Registration now uses actual face enrollment
- [ ] Login uses actual face verification

---

## 🔟 Performance & Load Test (Optional)

### Simple Stress Test:
```powershell
# Log 100 events rapidly
for ($i=1; $i -le 100; $i++) {
    curl.exe -X POST http://localhost:8080/api/events/log -H "Authorization: Bearer $studentToken" -F "studentId=1" -F "type=WINDOW_BLUR" -F "details=Test $i"
}
```

- [ ] All events logged successfully
- [ ] No database connection errors
- [ ] Response time acceptable
- [ ] Check backend logs for any errors

---

## ✅ Final Verification

### System Status Summary:
- [ ] ✅ Backend running without errors
- [ ] ✅ Database connected and populated
- [ ] ✅ Authentication working (register + login)
- [ ] ✅ JWT tokens generated and validated
- [ ] ✅ Role-based access control working
- [ ] ✅ Event logging functional
- [ ] ✅ Alert system triggered correctly
- [ ] ✅ All cheating detection rules working
- [ ] ✅ STUDENT endpoints accessible with STUDENT token
- [ ] ✅ ADMIN endpoints accessible with ADMIN token
- [ ] ✅ Unauthorized access properly blocked
- [ ] ✅ Database relationships intact

---

## 📊 Test Results Summary

### Total Tests: 30+

Fill in your results:
- ✅ Passed: ___ / 30
- ❌ Failed: ___ / 30
- ⏭️ Skipped: ___ / 30

### Critical Tests (Must Pass):
1. [ ] Backend starts successfully
2. [ ] Database tables created
3. [ ] User registration works
4. [ ] User login returns JWT token
5. [ ] JWT authentication validates correctly
6. [ ] Event logging saves to database
7. [ ] Alert rules trigger correctly
8. [ ] Role-based access control enforced

**If all 8 critical tests pass, your system is ready! 🎉**

---

## 🚀 Next Phase Readiness

Once all tests pass, you're ready to:

### ✅ Phase 1: Frontend Development
- Build student exam interface
- Build admin monitoring dashboard
- Integrate with backend APIs
- Implement WebSocket for real-time updates

### ✅ Phase 2: Integration Testing
- End-to-end testing with frontend
- User acceptance testing
- Security penetration testing
- Performance optimization

### ✅ Phase 3: Production Deployment
- Set up production environment
- Configure environment variables
- Enable HTTPS/SSL
- Set up monitoring and logging
- Deploy and go live!

---

**Date Completed:** _______________  
**Tested By:** _______________  
**Status:** [ ] READY  [ ] NEEDS FIXES

---

## 📝 Notes:

_Add any observations, issues found, or additional tests performed:_

```
Example:
- All tests passed on first run
- Database connection stable
- JWT authentication working perfectly
- Ready to start frontend development
```
