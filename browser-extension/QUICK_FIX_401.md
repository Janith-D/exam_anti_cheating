# ⚡ QUICK FIX: 401 Error

## Problem:
```
HTTP 401: Unauthorized
```

**Cause:** JWT token expired or invalid

---

## ✅ Solution:

### 1. Check Backend Running
```powershell
curl.exe http://localhost:8080/health
```
Expected: `{"status":"UP"}`

If not running:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

---

### 2. Get Fresh Token
```powershell
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=YOUR_USERNAME" `
  -F "password=YOUR_PASSWORD" `
  -F "image=@C:\path\to\your\photo.jpg"
```

**Copy the `token` value!**

---

### 3. Reload Extension
```
1. Open chrome://extensions/
2. Click reload icon 🔄
```

---

### 4. Save New Token
```
1. Click extension icon
2. Paste JWT token
3. Enter Student ID (from login response)
4. Click "Save Credentials"
```

---

### 5. Test
```
1. Click "Start Monitoring"
2. Try Ctrl+C, Ctrl+V
3. Check console
```

**Expected:**
```
✅ Credentials loaded into cache
✅ Event logged: COPY
✅ Event logged: PASTE
```

**No 401 errors!** 🎉

---

## Why This Happens:

JWT tokens expire after 24 hours. Login again to get a fresh token.

---

## What Was Fixed:

✅ Better error handling for 401  
✅ Use cached credentials consistently  
✅ Suppress background event 401 spam  
✅ Clear error messages  

---

**Get a fresh token and reload extension!** 🚀
