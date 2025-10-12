# Quick Fix Guide - Still Getting "Unauthorized"

## Immediate Actions

### Step 1: Run the Debug Script
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\detailed-test.ps1
```
This will test the complete flow and show you exactly where the issue is.

### Step 2: Check Backend Logs
I've added detailed logging to help debug. After running the test, check your backend terminal for logs like:
- "JWT Filter - Authorization Header: Present/Missing"
- "JWT Filter - Extracted username: ..."
- "JWT Filter - Authentication successful..."

---

## Common Causes and Solutions

### Issue 1: Token Format Wrong
**Symptoms:** Always get 401, even right after login

**Check:**
- Header should be: `Authorization: Bearer <token>`
- NOT: `Authorization: <token>`
- NOT: `Bearer: <token>`
- Must have a SPACE after "Bearer"

**Fix (curl):**
```powershell
$token = "your_token_here"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\image.jpg"
```

### Issue 2: Backend Not Restarted
**Symptoms:** Changes don't take effect

**Fix:**
1. Stop the backend (Ctrl+C in the terminal)
2. Restart it:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

### Issue 3: Wrong Role
**Symptoms:** Token works but still get 403/401

**Check:** Make sure you registered with `role=STUDENT` (uppercase)

**Fix:** Register a new user with the correct role

### Issue 4: Token Expired
**Symptoms:** Worked before, now doesn't

**Fix:** Login again to get a fresh token (tokens expire after 24 hours)

### Issue 5: Using Wrong Endpoint
**Common mistake:** Trying to hit enrollment before getting a token

**Correct order:**
1. POST `/api/auth/register` (no auth needed)
2. POST `/api/auth/login` (no auth needed) → Get token
3. POST `/api/enrollment/enroll` (needs token)

---

## Testing Methods

### Method 1: Automated Script (Recommended)
```powershell
.\detailed-test.ps1
```
Choose option 2 to run the full flow

### Method 2: Manual cURL Commands
```powershell
# 1. Register
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=student1" `
  -F "password=Pass123!" `
  -F "email=student1@test.com" `
  -F "firstName=John" `
  -F "lastName=Doe" `
  -F "role=STUDENT" `
  -F "studentId=S001" `
  -F "image=@C:\path\to\image.jpg"

# 2. Login (save the token!)
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=student1" `
  -F "password=Pass123!" `
  -F "image=@C:\path\to\image.jpg"

# 3. Use enrollment endpoint
$token = "paste_token_from_step_2"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\image.jpg"
```

### Method 3: Postman
1. **Register**: POST to `/api/auth/register` with form-data
2. **Login**: POST to `/api/auth/login` with form-data, copy the `token` from response
3. **Enrollment**: 
   - POST to `/api/enrollment/enroll`
   - Go to Headers tab
   - Add: Key=`Authorization`, Value=`Bearer <paste-token-here>`
   - Go to Body tab → form-data → add studentId and image

---

## Debug Checklist

Run through this checklist:

- [ ] Backend is running on port 8080
  - Test: `curl.exe http://localhost:8080/health`
  
- [ ] Can register a user successfully
  - Test: Run registration command
  
- [ ] Can login and get a token
  - Test: Run login command
  - Verify response contains `"token": "eyJ..."`
  
- [ ] Token is not empty or null
  - Check: Token should start with "eyJ" and have 3 parts (separated by dots)
  
- [ ] Using correct header format
  - Format: `Authorization: Bearer <token>`
  - Note the space after "Bearer"
  
- [ ] Token is being sent in the request
  - Check curl output with `-v` flag to see request headers
  
- [ ] Backend logs show token received
  - Look for: "JWT Filter - Authorization Header: Present"
  
- [ ] User has STUDENT role
  - Check login response for `"role": "STUDENT"`

---

## Still Not Working?

### Check Backend Logs
Look for these messages in your Spring Boot terminal:
```
JWT Filter - Request URI: /api/enrollment/enroll
JWT Filter - Authorization Header: Present
JWT Filter - Extracted username: student1
JWT Filter - Authentication successful for user: student1 with roles: [STUDENT]
```

### If you see "Authorization Header: Missing":
→ Token is not being sent properly in your request

### If you see "Failed to extract username":
→ Token is invalid or malformed

### If you see "Token validation failed":
→ Token is expired or doesn't match the user

### Enable More Logging
In `application.properties`:
```properties
logging.level.org.springframework.security=TRACE
logging.level.com.example.anti_cheating_backend.security=DEBUG
```

---

## Example of Successful Flow

```
> curl.exe http://localhost:8080/api/auth/register ...
{"message":"Registered and enrolled successfully","userId":1,"enrollmentId":1}

> curl.exe http://localhost:8080/api/auth/login ...
{"token":"eyJhbGciOiJIUzUxMiJ9.eyJzdWIi...","userName":"student1","role":"STUDENT"}

> $token = "eyJhbGciOiJIUzUxMiJ9.eyJzdWIi..."
> curl.exe -H "Authorization: Bearer $token" ...
{"message":"Enrolled successfully","enrollmentId":2}
```

---

## Get Help

If still not working, please provide:
1. Output from `.\detailed-test.ps1`
2. Backend logs (from Spring Boot terminal)
3. The exact curl command you're using
4. The exact error response you're getting
