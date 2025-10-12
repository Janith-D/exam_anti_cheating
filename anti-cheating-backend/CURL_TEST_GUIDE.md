# Quick cURL Test Commands for PowerShell

## Prerequisites
1. Backend must be running on http://localhost:8080
2. Have a test image ready (e.g., `C:\Users\YourName\Pictures\photo.jpg`)

---

## Step 1: Check if Backend is Running
```powershell
curl.exe http://localhost:8080/health
```

Expected output: `{"status":"UP","message":"Anti-Cheating Backend is running","timestamp":...}`

---

## Step 2: Register a New User
Replace `C:\path\to\image.jpg` with your actual image path:

```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=testuser123" `
  -F "password=MyPassword123!" `
  -F "email=test123@example.com" `
  -F "firstName=John" `
  -F "lastName=Doe" `
  -F "role=STUDENT" `
  -F "studentId=STU12345" `
  -F "image=@C:\path\to\image.jpg"
```

**Save the `userId` from the response!**

Example response:
```json
{
  "message": "Registered and enrolled successfully",
  "userId": 1,
  "enrollmentId": 1
}
```

---

## Step 3: Login to Get JWT Token
```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testuser123" `
  -F "password=MyPassword123!" `
  -F "image=@C:\path\to\image.jpg"
```

**Copy the entire `token` value from the response!**

Example response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlcjEyMyIsImlhdCI6MTY5NzAzMjgwMCwiZXhwIjoxNjk3MTE5MjAwfQ.xxx",
  "userName": "testuser123",
  "role": "STUDENT",
  "verified": true,
  "activationAllowed": true
}
```

---

## Step 4: Use Enrollment Endpoint with Token
Replace `YOUR_TOKEN_HERE` with the token from Step 3, and `1` with your userId:

```powershell
$token = "YOUR_TOKEN_HERE"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\image.jpg"
```

---

## Alternative: Get Enrollment Info
```powershell
$token = "YOUR_TOKEN_HERE"
curl.exe -X GET http://localhost:8080/api/enrollment/1 `
  -H "Authorization: Bearer $token"
```

---

## Common Errors and Fixes

### Error: "Unauthorized"
- **Cause**: No token or invalid token
- **Fix**: Make sure you login first and use the token correctly
- **Format**: `Authorization: Bearer YOUR_TOKEN` (note the space after "Bearer")

### Error: "403 Forbidden"
- **Cause**: Token valid but wrong role
- **Fix**: Make sure you registered with `role=STUDENT`

### Error: "ML service connection failed"
- **Cause**: Python ML service not running
- **Fix Option 1**: Start the ML service on port 5000
- **Fix Option 2**: Disable ML service in `application.properties`:
  ```properties
  ml.service.enabled=false
  ```

### Error: "curl: command not found"
- **Fix**: Use `curl.exe` instead of `curl` in PowerShell

---

## Complete Example Workflow

```powershell
# 1. Check health
curl.exe http://localhost:8080/health

# 2. Register
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=student1" `
  -F "password=Pass123!" `
  -F "email=student1@test.com" `
  -F "firstName=Student" `
  -F "lastName=One" `
  -F "role=STUDENT" `
  -F "studentId=S001" `
  -F "image=@C:\Users\YourName\Pictures\face.jpg"

# 3. Login (copy the token from response)
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=student1" `
  -F "password=Pass123!" `
  -F "image=@C:\Users\YourName\Pictures\face.jpg"

# 4. Use the enrollment endpoint (paste your token)
$token = "paste_your_token_here"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\Users\YourName\Pictures\face.jpg"
```

---

## Testing with Postman

If you prefer Postman:

1. **Health Check**:
   - Method: GET
   - URL: `http://localhost:8080/health`

2. **Register**:
   - Method: POST
   - URL: `http://localhost:8080/api/auth/register`
   - Body: form-data (add all fields as text, image as file)

3. **Login**:
   - Method: POST
   - URL: `http://localhost:8080/api/auth/login`
   - Body: form-data
   - Copy token from response

4. **Enrollment**:
   - Method: POST
   - URL: `http://localhost:8080/api/enrollment/enroll`
   - Headers: Add `Authorization` with value `Bearer YOUR_TOKEN`
   - Body: form-data

---

## Notes
- JWT tokens expire after 24 hours
- Always use the same person's face in images for registration, login, and enrollment
- The backend already enrolls faces during registration, so the separate enrollment endpoint might create a duplicate enrollment
