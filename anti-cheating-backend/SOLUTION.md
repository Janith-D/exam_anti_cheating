# SOLUTION: 401 Unauthorized on Enrollment Endpoint

## 🎯 ROOT CAUSE IDENTIFIED

The issue was in the `AuthService.loadUserByUsername()` method. When creating user authorities, it was setting:
```java
.authorities(student.getRole().name())  // e.g., "STUDENT"
```

But Spring Security's `@PreAuthorize("hasRole('STUDENT')")` expects authorities to be prefixed with "ROLE_":
```java
.authorities("ROLE_" + student.getRole().name())  // e.g., "ROLE_STUDENT"
```

## ✅ WHAT WAS FIXED

**File:** `src/main/java/com/example/anti_cheating_backend/service/AuthService.java`

**Change:**
```java
// BEFORE (Wrong - causes 401)
return org.springframework.security.core.userdetails.User.builder()
    .username(student.getUserName())
    .password(student.getPassword())
    .authorities(student.getRole().name())  // ❌ Returns "STUDENT"
    .build();

// AFTER (Correct - fixes 401)
return org.springframework.security.core.userdetails.User.builder()
    .username(student.getUserName())
    .password(student.getPassword())
    .authorities("ROLE_" + student.getRole().name())  // ✅ Returns "ROLE_STUDENT"
    .build();
```

## 🚀 HOW TO APPLY THE FIX

### Step 1: The code has already been updated!
The fix is already in your `AuthService.java` file.

### Step 2: RESTART YOUR BACKEND
**This is CRITICAL!** The fix won't work until you restart:

1. Stop the current backend (press `Ctrl+C` in the terminal)
2. Restart it:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

### Step 3: Test the fix
Run the verification script:
```powershell
.\verify-fix.ps1
```

Or test manually:
```powershell
# 1. Register
curl.exe -X POST http://localhost:8080/api/auth/register `
  -F "userName=newstudent" `
  -F "password=Pass123!" `
  -F "email=new@test.com" `
  -F "firstName=New" `
  -F "lastName=Student" `
  -F "role=STUDENT" `
  -F "studentId=S999" `
  -F "image=@C:\path\to\image.jpg"

# 2. Login
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=newstudent" `
  -F "password=Pass123!" `
  -F "image=@C:\path\to\image.jpg"

# Copy the token from the response!

# 3. Test enrollment (should work now!)
$token = "PASTE_YOUR_TOKEN_HERE"
curl.exe -X POST http://localhost:8080/api/enrollment/enroll `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "image=@C:\path\to\image.jpg"
```

## 📊 EXPECTED RESULTS

### Before Fix:
```
POST /api/enrollment/enroll with Bearer token
→ 401 Unauthorized
Backend logs: "JWT Filter - Authentication successful for user: student1 with roles: [STUDENT]"
→ @PreAuthorize("hasRole('STUDENT')") fails because it expects "ROLE_STUDENT"
```

### After Fix:
```
POST /api/enrollment/enroll with Bearer token
→ 200 OK or 201 Created
Backend logs: "JWT Filter - Authentication successful for user: student1 with roles: [ROLE_STUDENT]"
→ @PreAuthorize("hasRole('STUDENT')") succeeds because authority is "ROLE_STUDENT"
```

## 🔍 HOW TO VERIFY IT'S FIXED

After restarting the backend, check the logs when you make a request. You should see:

```
JWT Filter - Request URI: /api/enrollment/enroll
JWT Filter - Authorization Header: Present
JWT Filter - Extracted token (first 20 chars): eyJhbGciOiJIUzUxMiJ9...
JWT Filter - Extracted username: newstudent
JWT Filter - User details loaded for: newstudent
Loading user: newstudent with role: STUDENT
JWT Filter - Authentication successful for user: newstudent with roles: [ROLE_STUDENT]
```

**Key indicators:**
- ✅ "roles: [ROLE_STUDENT]" (with ROLE_ prefix)
- ✅ No 401 error
- ✅ Request processes successfully

## ❓ WHY THIS HAPPENED

Spring Security has two ways to check roles:

1. **hasRole('STUDENT')** - Automatically adds "ROLE_" prefix
   - Checks if user has authority: "ROLE_STUDENT"
   
2. **hasAuthority('STUDENT')** - Uses exact match
   - Checks if user has authority: "STUDENT"

Your code used `@PreAuthorize("hasRole('STUDENT')")` which expects "ROLE_STUDENT", but the authorities were set as "STUDENT" without the prefix.

## 🛠️ ALTERNATIVE SOLUTIONS

If you prefer not to add "ROLE_" prefix, you could change the annotations:

```java
// Option 1: Add ROLE_ prefix to authorities (✅ Applied)
@PreAuthorize("hasRole('STUDENT')")  // Expects ROLE_STUDENT
// Set authority as: "ROLE_STUDENT"

// Option 2: Use hasAuthority instead (Alternative)
@PreAuthorize("hasAuthority('STUDENT')")  // Expects STUDENT
// Set authority as: "STUDENT"
```

I chose Option 1 because it's more conventional in Spring Security applications.

## 📝 QUICK TEST CHECKLIST

- [ ] Code is updated in AuthService.java
- [ ] Backend is restarted
- [ ] Can register a new user
- [ ] Can login and get a token
- [ ] Token works with /api/enrollment/enroll endpoint
- [ ] Backend logs show "roles: [ROLE_STUDENT]"

## 🎉 EXPECTED OUTCOME

After applying this fix and restarting your backend:
- ✅ Login will still work (no changes to login flow)
- ✅ JWT token will be generated with correct roles
- ✅ @PreAuthorize("hasRole('STUDENT')") will recognize the role
- ✅ /api/enrollment/enroll endpoint will accept authenticated requests
- ✅ No more 401 Unauthorized errors!

---

**If you're STILL getting 401 after restarting:**
1. Make sure you registered/logged in AFTER the restart (old tokens won't have the fix)
2. Check the backend logs for the exact error
3. Run `.\verify-fix.ps1` to get detailed diagnostics
