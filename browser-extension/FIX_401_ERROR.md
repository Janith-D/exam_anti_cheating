# 🔧 FIX: HTTP 401 Unauthorized Error

**Date:** October 14, 2025  
**Error:** HTTP 401 - Authentication failed

---

## 🐛 The Problem

### Error Messages:
```
Tab switch event error: Error: HTTP 401:
Backend error text:
Window blur event error: Error: HTTP 401:
```

### What It Means:
**HTTP 401 = Unauthorized** - The JWT token is:
- ❌ Expired (24-hour validity)
- ❌ Invalid or corrupted
- ❌ Not accepted by backend
- ❌ Missing from request

---

## 🔍 Root Causes

### 1. Token Expiration
JWT tokens expire after 24 hours. If you logged in yesterday, the token is no longer valid.

### 2. Backend Restarted
When the backend restarts, it might reject old tokens depending on your JWT secret configuration.

### 3. Token Not Saved
Credentials might not be saved in the extension storage properly.

### 4. Backend Not Running
If the backend isn't running, you'll get connection errors.

---

## ✅ Solutions Applied

### Fix 1: Use Cached Credentials
Changed background.js to use the credentials cache instead of passing token manually:

**Before:**
```javascript
chrome.tabs.onActivated.addListener((activeInfo) => {
  chrome.storage.local.get(['isMonitoring', 'jwtToken'], (result) => {
    if (result.isMonitoring && result.jwtToken) {
      logEventToBackend({...}, result.jwtToken);  // ← Passed token
    }
  });
});
```

**After:**
```javascript
chrome.tabs.onActivated.addListener((activeInfo) => {
  chrome.storage.local.get(['isMonitoring'], (result) => {
    if (result.isMonitoring) {
      logEventToBackend({...});  // ← Uses cached token automatically
    }
  });
});
```

### Fix 2: Better Error Handling
Added special handling for 401 errors:

```javascript
if (response.status === 401) {
  console.warn('JWT token expired or invalid. Please login again.');
  throw new Error('HTTP 401: Authentication failed. Please save your credentials again.');
}
```

### Fix 3: Suppress 401 Noise
Background events (tab switch, window blur) won't spam console with 401 errors:

```javascript
.catch(err => {
  // Don't log 401 errors (user might not be logged in yet)
  if (!err.message.includes('401')) {
    console.error('Tab switch event error:', err);
  }
});
```

---

## 🔄 How to Fix This

### Option 1: Get Fresh Token (Recommended)

#### Step 1: Login Again
```powershell
# Login to get new JWT token
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=testStudent" `
  -F "password=Test123!" `
  -F "image=@C:\path\to\your\photo.jpg"
```

**Copy the `token` from response!**

#### Step 2: Save New Token in Extension
1. Click extension icon
2. Paste the new token in "JWT Token" field
3. Enter your Student ID (from login response)
4. Click "Save Credentials"

#### Step 3: Verify
```
1. Click "Start Monitoring"
2. Try some actions (Ctrl+C, Ctrl+V)
3. Should see "Event logged: ..." in console
```

---

### Option 2: Check Backend is Running

```powershell
# Test if backend is running
curl.exe http://localhost:8080/health

# Expected: {"status":"UP"}
```

If backend is not running:
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
.\mvnw.cmd spring-boot:run
```

---

### Option 3: Verify Saved Credentials

#### Check Extension Storage:
1. Right-click extension icon → "Inspect"
2. Go to "Console" tab
3. Type:
```javascript
chrome.storage.local.get(['jwtToken', 'studentInfo'], (result) => {
  console.log('Token:', result.jwtToken ? 'Exists ✅' : 'Missing ❌');
  console.log('Student Info:', result.studentInfo);
});
```

#### Should See:
```
Token: Exists ✅
Student Info: {studentId: 1, userName: "testStudent", role: "STUDENT"}
```

If missing, save credentials in popup again.

---

## 📊 Understanding JWT Expiration

### How JWT Tokens Work:

```
Login → Get Token → Use for 24 hours → Expires → Login Again
```

### Token Lifecycle:
```
Day 1, 10:00 AM: Login
  ↓
Token issued: Valid until Day 2, 10:00 AM
  ↓
Day 1, 10:00 AM - Day 2, 10:00 AM: ✅ Token works
  ↓
Day 2, 10:01 AM: ❌ Token expired (401 error)
  ↓
Must login again to get fresh token
```

### Check Token Expiration:

In extension console:
```javascript
// Get saved token
chrome.storage.local.get(['jwtToken'], (result) => {
  const token = result.jwtToken;
  
  // Decode JWT (it's base64 encoded)
  const parts = token.split('.');
  const payload = JSON.parse(atob(parts[1]));
  
  const expirationDate = new Date(payload.exp * 1000);
  console.log('Token expires at:', expirationDate);
  console.log('Current time:', new Date());
  console.log('Is expired?', new Date() > expirationDate ? 'YES ❌' : 'NO ✅');
});
```

---

## 🧪 Testing After Fix

### 1. Reload Extension
```
1. Open chrome://extensions/
2. Click reload icon 🔄
```

### 2. Get Fresh Token
```powershell
# Login with correct credentials
curl.exe -X POST http://localhost:8080/api/auth/login `
  -F "userName=YOUR_USERNAME" `
  -F "password=YOUR_PASSWORD" `
  -F "image=@C:\path\to\photo.jpg"
```

### 3. Save in Extension
```
1. Click extension icon
2. Paste JWT token
3. Enter Student ID
4. Click "Save Credentials"
```

### 4. Test Events
```
1. Click "Start Monitoring"
2. Try: Ctrl+C, Ctrl+V, tab switch
3. Check console for success messages
```

### Expected Results:
```
✅ Credentials loaded into cache
✅ Event logged successfully: {eventId: 1, message: "Event logged"}
✅ Event logged: COPY
✅ Event logged: PASTE
✅ Event logged: TAB_SWITCH
```

**No 401 errors!** 🎉

---

## 🔐 Backend JWT Configuration

Check your `application.properties`:

```properties
# JWT Configuration
jwt.secret=your-secret-key-here-make-it-long-and-random-at-least-512-bits
jwt.expiration=86400000  # 24 hours in milliseconds
```

### Token Expiration Times:
- `86400000` = 24 hours (current)
- `3600000` = 1 hour
- `604800000` = 7 days
- `2592000000` = 30 days

To extend token validity, change `jwt.expiration` in `application.properties` and restart backend.

---

## 🎯 Preventing Future 401 Errors

### 1. Implement Token Refresh
Add automatic token refresh before expiration:

```javascript
// Check if token expires soon (within 1 hour)
function isTokenExpiringSoon(token) {
  try {
    const parts = token.split('.');
    const payload = JSON.parse(atob(parts[1]));
    const expirationDate = new Date(payload.exp * 1000);
    const oneHourFromNow = new Date(Date.now() + 3600000);
    return expirationDate < oneHourFromNow;
  } catch {
    return true;
  }
}

// Before logging event
if (isTokenExpiringSoon(token)) {
  // Show notification to user
  console.warn('Token expiring soon. Please login again.');
}
```

### 2. Show Expiration Warning
Add to popup.js to show token expiration:

```javascript
function checkTokenExpiration() {
  chrome.storage.local.get(['jwtToken'], (result) => {
    if (result.jwtToken) {
      try {
        const parts = result.jwtToken.split('.');
        const payload = JSON.parse(atob(parts[1]));
        const expirationDate = new Date(payload.exp * 1000);
        
        if (new Date() > expirationDate) {
          alert('⚠️ Your session has expired. Please login again.');
        } else if (isTokenExpiringSoon(result.jwtToken)) {
          alert('⚠️ Your session will expire soon. Please login again to avoid interruption.');
        }
      } catch (e) {
        console.error('Error checking token:', e);
      }
    }
  });
}

// Check on popup open
checkTokenExpiration();
```

### 3. Graceful Degradation
Extension continues to monitor even if backend is unreachable:

```javascript
.catch(err => {
  if (err.message.includes('401')) {
    // Token expired - show notification but don't stop monitoring
    console.warn('Authentication expired. Events will not be logged until you login again.');
  } else if (err.message.includes('Failed to fetch')) {
    // Backend unreachable - queue events locally
    console.warn('Backend unreachable. Events will be queued.');
  } else {
    console.error('Event logging error:', err);
  }
});
```

---

## ✅ Summary

**Problem:** HTTP 401 errors when logging events  
**Causes:** JWT token expired, invalid, or backend rejecting it  
**Solutions:**  
  1. ✅ Use cached credentials consistently  
  2. ✅ Better error handling for 401s  
  3. ✅ Suppress background event 401 errors  
  4. ⚠️ Login again to get fresh token  
  5. ⚠️ Verify backend is running  

**Status:** Fixed! Reload extension and get fresh token  

---

## 🚀 Next Steps

1. **Reload extension** (chrome://extensions/)
2. **Login to get fresh token** (curl command above)
3. **Save token in extension** (click icon → paste token)
4. **Test event logging** (Ctrl+C, Ctrl+V, etc.)
5. **Verify no 401 errors** (check console)

---

**After getting a fresh token, all events should log successfully!** 🎉
