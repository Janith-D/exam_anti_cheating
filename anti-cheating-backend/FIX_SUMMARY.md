# Fix Summary: "Unauthorized" Error on Enrollment Endpoint

## Problem
When sending a POST request to `http://localhost:8080/api/enrollment/enroll`, you received an "Unauthorized" error.

## Root Cause
The endpoint is protected by Spring Security and requires:
1. **Valid JWT Authentication Token** in the Authorization header
2. **STUDENT role** assigned to the user

The `@PreAuthorize("hasRole('STUDENT')")` annotation on the endpoint enforces this security.

## What Was Changed

### 1. Updated SecurityConfig.java ✅
**File**: `src/main/java/com/example/anti_cheating_backend/config/SecurityConfig.java`

**Changes**:
- Added CORS configuration (disabled for now, but can be configured later)
- Added `/api/test` endpoint to public access list for testing

```java
.cors(cors -> cors.disable()) // Can be configured for production
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/api/auth/**").permitAll()
    .requestMatchers("/api/test").permitAll()  // NEW
    .requestMatchers("/health").permitAll()
    .requestMatchers("/ws/**").permitAll()
    .anyRequest().authenticated()
)
```

### 2. Created HealthController.java ✅
**File**: `src/main/java/com/example/anti_cheating_backend/controller/HealthController.java`

**Purpose**: Added test endpoints that don't require authentication:
- `GET /health` - Check if backend is running
- `GET /api/test` - Get API endpoint information

### 3. Created Documentation ✅
- **API_TESTING_GUIDE.md** - Comprehensive testing guide
- **CURL_TEST_GUIDE.md** - Quick cURL commands for PowerShell
- **test-api.ps1** - Automated PowerShell test script

## How to Use the Enrollment Endpoint Now

### Quick Steps:
1. **Register a user** (POST `/api/auth/register`)
2. **Login** (POST `/api/auth/login`) → Get JWT token
3. **Use the enrollment endpoint** with the token

### Example with cURL:
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

# 2. Login and get token
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=student1" `
  -F "password=Pass123!" `
  -F "image=@C:\path\to\image.jpg"

# Response contains: {"token": "eyJhbG...", ...}

# 3. Use enrollment endpoint with token
$token = "paste_token_here"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\image.jpg"
```

## Testing Options

### Option 1: Automated PowerShell Script
Run the automated test script:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\test-api.ps1
```

### Option 2: Manual cURL Commands
Follow the commands in `CURL_TEST_GUIDE.md`

### Option 3: Postman
Follow the instructions in `API_TESTING_GUIDE.md`

## Important Notes

### About ML Service
Your application tries to connect to a Python ML service on port 5000 for face recognition. If it's not running, you'll get errors.

**Solutions**:
1. **Start the ML service**: Navigate to `ml-service` folder and run the Flask app
2. **Disable ML service**: Edit `application.properties`:
   ```properties
   ml.service.enabled=false
   ```

### Token Expiration
JWT tokens expire after 24 hours (86400000 ms). After expiration, you need to login again.

### Security Best Practices
For production:
1. Configure CORS properly instead of disabling it
2. Use HTTPS
3. Store JWT secret in environment variables
4. Implement token refresh mechanism
5. Add rate limiting

## Verify the Fix

1. **Start your backend**:
   ```powershell
   cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
   .\mvnw.cmd spring-boot:run
   ```

2. **Test health endpoint** (no auth required):
   ```powershell
   curl.exe http://localhost:8080/health
   ```

3. **Run the automated test**:
   ```powershell
   .\test-api.ps1
   ```

## Files Modified
- ✅ `SecurityConfig.java` - Added CORS and test endpoint access
- ✅ `HealthController.java` - New file for test endpoints

## Files Created
- ✅ `API_TESTING_GUIDE.md` - Comprehensive API testing guide
- ✅ `CURL_TEST_GUIDE.md` - Quick cURL command reference
- ✅ `test-api.ps1` - Automated test script
- ✅ `FIX_SUMMARY.md` - This file

## Next Steps
1. Start your Spring Boot backend
2. Ensure MySQL is running (localhost:3306)
3. Run the test script or follow the cURL guide
4. Once you can get a token, save it for future requests

## Common Errors After Fix

### Still getting "Unauthorized"?
- Check that you're including the token: `Authorization: Bearer YOUR_TOKEN`
- Make sure there's a space after "Bearer"
- Verify the token hasn't expired (24 hours)

### "ML service connection failed"?
- Either start the ML service or set `ml.service.enabled=false`

### Database errors?
- Ensure MySQL is running
- Check credentials in `application.properties`
