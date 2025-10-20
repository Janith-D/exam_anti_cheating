# 🚨 IMMEDIATE FIX FOR 401 ERROR

## The Problem:
Your JWT token is **expired or invalid**. Backend is returning 401 Unauthorized.

---

## ✅ QUICK SOLUTION (3 Steps):

### Step 1: Check Backend is Running

Open PowerShell and run:
```powershell
Invoke-RestMethod -Uri http://localhost:8080/api/health
```

**If error:** Start your backend first!
```powershell
cd d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend
mvn spring-boot:run
```

---

### Step 2: Get Fresh Token via Postman/Browser

Since PowerShell login is failing, use **Postman** or **Browser Console**:

#### Using Browser Console (Chrome/Firefox):

1. Open any webpage
2. Press **F12** → Console tab
3. Paste this code:

```javascript
fetch('http://localhost:8080/api/auth/register', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    username: 'teststudent' + Date.now(),  // Unique username
    email: 'test' + Date.now() + '@example.com',
    password: 'test123',
    role: 'STUDENT'
  })
})
.then(r => r.json())
.then(data => {
  console.log('✅ SUCCESS!');
  console.log('Token:', data.token);
  console.log('Student ID:', data.userId);
  
  // Copy to clipboard
  navigator.clipboard.writeText(data.token);
  alert('Token copied to clipboard!\nStudent ID: ' + data.userId);
})
.catch(err => {
  console.error('❌ Error:', err);
  
  // Try login instead
  console.log('Trying login...');
  return fetch('http://localhost:8080/api/auth/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      username: 'teststudent',
      password: 'test123'
    })
  })
  .then(r => r.json())
  .then(data => {
    console.log('✅ LOGIN SUCCESS!');
    console.log('Token:', data.token);
    console.log('Student ID:', data.userId);
    navigator.clipboard.writeText(data.token);
    alert('Token copied to clipboard!\nStudent ID: ' + data.userId);
  });
});
```

**Expected:**
- Alert: "Token copied to clipboard!"
- Console shows your token and student ID

---

### Step 3: Save Token in Extension

1. **Click extension icon** in Chrome
2. **Enter:**
   - Student ID: (the ID from console, e.g., `9`)
   - Exam Session ID: `1`
   - JWT Token: **Ctrl+V** (token is in clipboard)
3. **Click "Save Credentials"**
4. **Click "Start Monitoring"**

---

## 🧪 TEST IT WORKS:

Press **Ctrl+C** on the page.

**Console should show:**
```
🔵 Logging event: KEY_COMBINATION | Blocked: Ctrl+C
✅ Event logged successfully: KEY_COMBINATION
📊 Event count updated: 1
```

**NO MORE 401 ERRORS!** ✅

---

## 🔧 Alternative: Use Existing Student

If you already have a student in the database (student ID 7 or 8), you can try to login with their credentials.

**Check database first:**
```sql
SELECT student_id, user_name, email, password 
FROM students 
WHERE role = 'STUDENT';
```

**But wait!** Passwords are bcrypt hashed, so you can't see them. You need to either:

1. **Remember the password** you used when registering
2. **Create a new student** (recommended - use browser console method above)

---

## 📋 What Went Wrong:

Your extension is logging events automatically:
- `WINDOW_BLUR` when you leave the window
- `TAB_SWITCH` when you switch tabs
- `SNAPSHOT` every 30 seconds

But the JWT token is expired, so all these requests fail with 401.

**Solution:** Fresh token = No more 401s!

---

## 🎯 DO THIS NOW:

1. **Open Chrome → F12 → Console**
2. **Paste the JavaScript code from Step 2**
3. **Press Enter**
4. **Wait for alert "Token copied!"**
5. **Open extension popup**
6. **Paste token (Ctrl+V)**
7. **Enter Student ID from alert**
8. **Enter Exam Session ID: 1**
9. **Click Save → Start Monitoring**
10. **Press Ctrl+C to test**

**Should work perfectly!** ✅

---

## 💡 WHY 401 HAPPENS:

```
Extension loads → Tries to log events automatically
                    ↓
              Check JWT token
                    ↓
        Token expired/invalid/missing
                    ↓
           Backend returns 401
                    ↓
      ❌ Failed to log event: HTTP 401
```

**Fix:** Get fresh valid token → Backend accepts requests → ✅ Success!

---

**Copy the JavaScript code above and run it in your browser console RIGHT NOW!** 🚀
