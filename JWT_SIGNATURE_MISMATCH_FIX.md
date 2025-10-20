# 🔧 FIX: JWT Signature Mismatch

**Date:** October 14, 2025  
**Error:** "JWT signature does not match locally computed signature"

---

## 🐛 The Error

```
ERROR: Failed to parse JWT: JWT signature does not match locally computed signature. 
JWT validity cannot be asserted and should not be trusted.
```

---

## 🔍 What This Means

The JWT token saved in your extension was created with a **different secret key** than the backend is currently using.

**How JWT Works:**
```
Login → Backend creates token with SECRET_KEY_A
  ↓
Token saved in extension
  ↓
Backend restarts (maybe SECRET_KEY changed to SECRET_KEY_B)
  ↓
Extension sends old token (signed with SECRET_KEY_A)
  ↓
Backend tries to verify with SECRET_KEY_B
  ↓
❌ MISMATCH → "signature does not match"
```

---

## ✅ Solution: Get Fresh Token

### Step 1: Login Again

```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=YOUR_USERNAME" `
  -F "password=YOUR_PASSWORD" `
  -F "image=@C:\path\to\your\photo.jpg"
```

**Example:**
```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testStudent" `
  -F "password=Test123!" `
  -F "image=@D:\photos\test.jpg"
```

### Step 2: Copy Response

Expected response:
```json
{
  "token": "eyJhbGciOiJIUzUxMiJ9.eyJzdWIiOiJ0ZXN0U3R1ZGVudCIsInJvbGUiOiJTVFVERU5UIiwic3R1ZGVudElkIjoxLCJpYXQiOjE2OTc...LONG_TOKEN_HERE",
  "type": "Bearer",
  "userName": "testStudent",
  "email": "test@student.com",
  "role": "STUDENT",
  "studentId": 1
}
```

**Copy the entire `token` value** (it's very long, starts with `eyJ`)

### Step 3: Reload Extension

```
1. Open chrome://extensions/
2. Find "Anti-Cheating Proctor Extension"
3. Click reload icon 🔄
```

### Step 4: Save New Token

```
1. Click extension icon
2. Paste the NEW token in "JWT Token" field
3. Enter Student ID (from login response, e.g., 1)
4. Click "Save Credentials"
```

### Step 5: Test

```
1. Click "Start Monitoring"
2. Try Ctrl+C (copy)
3. Check console
```

**Expected:**
```
✅ Event logged: KEY_COMBINATION
```

**Backend logs:**
```
✅ Received event log request - studentId: 1, type: KEY_COMBINATION
✅ Logged event: 1, type: KEY_COMBINATION
```

**No more signature mismatch errors!** 🎉

---

## 🔐 Check Backend JWT Secret

### In `application.properties`:

```properties
# JWT Configuration
jwt.secret=your-secret-key-here-make-it-long-and-random-at-least-512-bits
jwt.expiration=86400000
```

**Important:**
- If `jwt.secret` changes, ALL old tokens become invalid
- After backend restart, if secret is different, get new tokens
- Keep secret consistent across restarts

---

## 🎯 Why Tokens Become Invalid

| Reason | What Happened | Solution |
|--------|---------------|----------|
| Backend restarted | JWT secret changed | Login again |
| Token expired | 24 hours passed | Login again |
| Secret changed | Manually edited in properties | Login again |
| Different backend | Testing on different server | Login to that server |

---

## 📝 Quick Test Commands

### Test 1: Check Backend Health
```powershell
curl.exe http://localhost:8080/health
```
Expected: `{"status":"UP"}`

### Test 2: Login
```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testStudent" `
  -F "password=Test123!" `
  -F "image=@D:\photos\test.jpg"
```

### Test 3: Test Event Logging
```powershell
$token = "PASTE_NEW_TOKEN_HERE"
$studentId = 1

curl.exe -X POST "http://localhost:8080/api/events/log" `
  -H "Authorization: Bearer $token" `
  -F "studentId=$studentId" `
  -F "type=COPY" `
  -F "details=Test from PowerShell"
```

Expected: `{"message":"Event logged","eventId":1}`

---

## ✅ Checklist

After getting fresh token:

- [ ] Backend running (port 8080)
- [ ] Got fresh token via login
- [ ] Copied entire token (very long string)
- [ ] Reloaded extension
- [ ] Saved token in extension popup
- [ ] Entered correct Student ID
- [ ] Started monitoring
- [ ] Tested event (Ctrl+C)
- [ ] Console shows "Event logged: ..."
- [ ] Backend shows "Logged event: ..."
- [ ] No "signature does not match" error

---

## 🚀 Summary

**Error:** JWT signature mismatch  
**Cause:** Token created with different secret than backend is using  
**Solution:** Login again to get fresh token  
**Result:** Extension will work with new token  

---

**Get a fresh token by logging in again!** 🎉
