# API Testing Guide for Anti-Cheating Backend

## Issue: "Unauthorized" Error on `/api/enrollment/enroll`

### Root Cause
The endpoint requires authentication with a valid JWT token and `STUDENT` role. You cannot access it without first logging in.

---

## Step-by-Step Testing Process

### Step 1: Register a New User
**Endpoint:** `POST http://localhost:8080/api/auth/register`

**Request Type:** `multipart/form-data`

**Required Fields:**
- `userName`: Your username (string)
- `password`: Your password (string)
- `email`: Your email address (string)
- `firstName`: First name (string)
- `lastName`: Last name (string)
- `role`: Must be "STUDENT" (string)
- `image`: An image file (JPEG/PNG)
- `studentId`: Student ID (string, optional)

**Example using Postman:**
1. Select POST request
2. URL: `http://localhost:8080/api/auth/register`
3. Body → form-data
4. Add key-value pairs:
   - `userName` = `testuser`
   - `password` = `password123`
   - `email` = `test@example.com`
   - `firstName` = `John`
   - `lastName` = `Doe`
   - `role` = `STUDENT`
   - `studentId` = `STU001`
   - `image` = (select a file from your computer)

**Example using cURL (PowerShell):**
```powershell
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=testuser" `
  -F "password=password123" `
  -F "email=test@example.com" `
  -F "firstName=John" `
  -F "lastName=Doe" `
  -F "role=STUDENT" `
  -F "studentId=STU001" `
  -F "image=@C:\path\to\your\image.jpg"
```

**Expected Response:**
```json
{
  "message": "Registered and enrolled successfully",
  "userId": 1,
  "enrollmentId": 1
}
```

---

### Step 2: Login to Get JWT Token
**Endpoint:** `POST http://localhost:8080/api/auth/login`

**Request Type:** `multipart/form-data`

**Required Fields:**
- `userName`: Your username (string)
- `password`: Your password (string)
- `image`: An image file (JPEG/PNG) - should be a photo of the same person

**Example using Postman:**
1. Select POST request
2. URL: `http://localhost:8080/api/auth/login`
3. Body → form-data
4. Add key-value pairs:
   - `userName` = `testuser`
   - `password` = `password123`
   - `image` = (select the same or similar image)

**Example using cURL (PowerShell):**
```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testuser" `
  -F "password=password123" `
  -F "image=@C:\path\to\your\image.jpg"
```

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0dXNlciIsImlhdCI6MTY5NzAzMjgwMCwiZXhwIjoxNjk3MTE5MjAwfQ...",
  "userName": "testuser",
  "role": "STUDENT",
  "verified": true,
  "activationAllowed": true
}
```

**Important:** Copy the `token` value from the response!

---

### Step 3: Use the Enrollment Endpoint
**Endpoint:** `POST http://localhost:8080/api/enrollment/enroll`

**Request Type:** `multipart/form-data`

**Headers:**
- `Authorization`: `Bearer YOUR_JWT_TOKEN_HERE`

**Required Fields:**
- `studentId`: The user ID from registration (Long/number)
- `image`: An image file (JPEG/PNG)

**Example using Postman:**
1. Select POST request
2. URL: `http://localhost:8080/api/enrollment/enroll`
3. Headers:
   - Key: `Authorization`
   - Value: `Bearer eyJhbGciOiJIUzUxMiJ9...` (paste your token after "Bearer ")
4. Body → form-data
5. Add key-value pairs:
   - `studentId` = `1`
   - `image` = (select a file)

**Example using cURL (PowerShell):**
```powershell
$token = "YOUR_JWT_TOKEN_HERE"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\your\image.jpg"
```

---

## Common Issues and Solutions

### 1. "Unauthorized" Error
**Problem:** No JWT token or invalid token
**Solution:** Make sure you:
- First login to get a token
- Include the token in Authorization header as `Bearer YOUR_TOKEN`
- Token format: `Authorization: Bearer eyJhbGciOiJIUzUxMiJ9...`

### 2. "403 Forbidden" Error
**Problem:** Token is valid but user doesn't have required role
**Solution:** Make sure you registered with `role=STUDENT`

### 3. "ML service connection failed"
**Problem:** ML service (Python Flask) is not running
**Solution:** Either:
- Start the ML service on port 5000, OR
- Disable ML service in `application.properties`: set `ml.service.enabled=false`

### 4. "Face enrollment/verification failed"
**Problem:** ML service rejected the image
**Solution:**
- Use a clear image with a visible face
- Ensure image is in JPEG/PNG format
- Use the same person's face for registration and login

### 5. Token Expired
**Problem:** Token is valid for 24 hours (86400000 ms)
**Solution:** Login again to get a new token

---

## Testing with Postman Collection

### Quick Test Collection:
1. **Register**: Save the `userId` from response
2. **Login**: Save the `token` from response
3. **Enroll**: Use saved `userId` and `token`

---

## Database Check
If you want to verify the data was saved:

```sql
-- Check registered users
SELECT * FROM students;

-- Check enrollments
SELECT * FROM enrollments;
```

---

## Notes
- The registration already creates an enrollment, so the separate enrollment endpoint might be redundant
- JWT tokens expire after 24 hours by default
- Make sure MySQL is running on localhost:3306
- Database name: `anti_cheating_db`
