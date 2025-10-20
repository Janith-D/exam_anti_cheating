# 🛡️ Action Blocking Test Guide

## ✅ What I Just Fixed

### Triple Layer Protection Added:
1. **JavaScript Event Prevention**: `e.preventDefault()` + `e.stopPropagation()` + `e.stopImmediatePropagation()`
2. **Event Capture Phase**: All listeners now use `capture: true` to intercept events BEFORE other handlers
3. **CSS User Selection Blocking**: Automatically disables text selection when monitoring starts

## 🔴 CRITICAL: Before Testing

### Step 1: Get Fresh JWT Token
Your current token has a signature mismatch. Get a new one:

```powershell
# Run this PowerShell script
.\get-fresh-token.ps1
```

**OR manually via curl:**
```powershell
curl -X POST http://localhost:8080/api/auth/login `
  -H "Content-Type: application/json" `
  -d '{\"username\":\"teacher\",\"password\":\"teacher123\"}'
```

**Copy the token from the response!**

### Step 2: Reload Extension
1. Open Chrome: `chrome://extensions/`
2. Find "Anti-Cheating Extension"
3. Click the **Reload** button (↻)
4. Verify no errors in console

### Step 3: Configure Extension
1. Click extension icon in Chrome toolbar
2. Paste the **FRESH token** you just got
3. Select your exam session (ID 1 or 2)
4. Click **"Start Monitoring"**
5. Extension popup should close (this is normal!)

## 🧪 Testing Checklist

### Test 1: Copy Protection
**What to test:** Select text and press Ctrl+C

**Expected behavior:**
- ✅ Alert shows: "⚠️ Ctrl+C is disabled during the exam!"
- ✅ Text is NOT copied to clipboard
- ✅ Console shows: "Event logged: KEY_COMBINATION"
- ✅ Database has new event: `SELECT * FROM events WHERE type='KEY_COMBINATION'`

**How to verify:**
1. Select some text on the page
2. Press **Ctrl+C**
3. Try to paste elsewhere (Ctrl+V in Notepad) - should be empty!

---

### Test 2: Paste Protection
**What to test:** Copy something outside, try to paste with Ctrl+V

**Expected behavior:**
- ✅ Alert shows: "⚠️ Ctrl+V is disabled during the exam!"
- ✅ Nothing is pasted
- ✅ Console shows: "Event logged: KEY_COMBINATION"
- ✅ Database has new event

**How to verify:**
1. Open Notepad, type "test", copy it (Ctrl+C)
2. Go back to exam page
3. Press **Ctrl+V** in an input field
4. Nothing should happen (no text appears)

---

### Test 3: Right-Click Protection
**What to test:** Right-click on page

**Expected behavior:**
- ✅ Alert shows: "⚠️ Right-click is disabled during the exam!"
- ✅ Context menu does NOT appear
- ✅ Console shows: "Event logged: RIGHT_CLICK"
- ✅ Database has new event with type='RIGHT_CLICK'

**How to verify:**
1. Right-click anywhere on the page
2. Context menu should NOT show
3. Alert should appear immediately

---

### Test 4: Cut Protection
**What to test:** Select text and press Ctrl+X

**Expected behavior:**
- ✅ Alert shows: "⚠️ Ctrl+X is disabled during the exam!"
- ✅ Text is NOT cut
- ✅ Console shows event logged

---

### Test 5: Find Protection
**What to test:** Press Ctrl+F

**Expected behavior:**
- ✅ Alert shows: "⚠️ Ctrl+F is disabled during the exam!"
- ✅ Find dialog does NOT open
- ✅ Console shows event logged

---

### Test 6: New Tab Protection
**What to test:** Press Ctrl+T

**Expected behavior:**
- ✅ Alert shows: "⚠️ Ctrl+T is disabled during the exam!"
- ✅ New tab does NOT open
- ✅ Console shows event logged

---

### Test 7: DevTools Protection
**What to test:** Press F12

**Expected behavior:**
- ✅ Alert shows: "⚠️ Developer tools are disabled during the exam!"
- ✅ DevTools do NOT open
- ✅ Console shows: "Event logged: BROWSER_DEVTOOLS"
- ✅ Database has event with type='BROWSER_DEVTOOLS'

---

### Test 8: Text Selection Disabled
**What to test:** Try to select text with mouse

**Expected behavior:**
- ✅ Text should NOT be selectable (CSS prevents it)
- ✅ Mouse drag should NOT highlight text
- ✅ This is an extra layer on top of JavaScript blocking

---

## 🐛 If Actions Are Still NOT Blocked

### Debug Step 1: Check Monitoring Status
Open browser console (F12 before starting monitoring), then run:
```javascript
chrome.storage.local.get(['isMonitoring'], console.log)
```
**Expected:** `{ isMonitoring: true }`

### Debug Step 2: Verify Content Script Loaded
Look for this in console:
```
Anti-Cheating Extension: Content script loaded
Monitoring started
Applied selection blocking CSS
```

### Debug Step 3: Check for Conflicting Extensions
Other extensions might override event handlers:
1. Open `chrome://extensions/`
2. Temporarily **disable ALL other extensions**
3. Test again

### Debug Step 4: Test in Incognito Mode
1. Go to `chrome://extensions/`
2. Find your extension
3. Enable **"Allow in incognito"**
4. Open incognito window
5. Test blocking there (fewer conflicts)

### Debug Step 5: Check Backend Connection
In browser console:
```javascript
chrome.storage.local.get(['apiToken', 'examSessionId', 'studentId'], console.log)
```
**Expected:** All three values should be present

### Debug Step 6: Verify Event Capture Phase
The code now uses `addEventListener('copy', handler, true)` where `true` = capture phase.
This should intercept events BEFORE any other handlers on the page.

---

## 📊 Database Verification

### Check Events Table
```sql
-- All blocked attempts from your student (ID 7)
SELECT * FROM events 
WHERE student_id = 7 
ORDER BY timestamp DESC 
LIMIT 20;

-- Count by event type
SELECT type, COUNT(*) as count 
FROM events 
WHERE student_id = 7 
GROUP BY type;

-- Recent KEY_COMBINATION events (Ctrl+C, Ctrl+V, etc.)
SELECT * FROM events 
WHERE student_id = 7 
AND type = 'KEY_COMBINATION' 
ORDER BY timestamp DESC;

-- Check if alerts were generated
SELECT a.alert_type, a.severity, e.type, e.details 
FROM alerts a
JOIN events e ON a.event_id = e.event_id
WHERE e.student_id = 7
ORDER BY a.timestamp DESC;
```

---

## 🎯 Expected Results Summary

After testing all 8 scenarios:

| Test | Action | Should Block? | Alert? | Database Event? |
|------|--------|---------------|--------|-----------------|
| 1 | Ctrl+C | ✅ YES | ✅ YES | KEY_COMBINATION |
| 2 | Ctrl+V | ✅ YES | ✅ YES | KEY_COMBINATION |
| 3 | Right-click | ✅ YES | ✅ YES | RIGHT_CLICK |
| 4 | Ctrl+X | ✅ YES | ✅ YES | KEY_COMBINATION |
| 5 | Ctrl+F | ✅ YES | ✅ YES | KEY_COMBINATION |
| 6 | Ctrl+T | ✅ YES | ✅ YES | KEY_COMBINATION |
| 7 | F12 | ✅ YES | ✅ YES | BROWSER_DEVTOOLS |
| 8 | Text select | ✅ YES | ❌ NO | ❌ NO |

---

## 🔧 Technical Details of Fix

### What Was Wrong Before:
- Only used `e.preventDefault()` which can be overridden
- Event listeners on bubble phase (too late)
- No CSS-level protection

### What's Fixed Now:
```javascript
// BEFORE (weak)
document.addEventListener('copy', (e) => {
  e.preventDefault();
});

// AFTER (strong)
document.addEventListener('copy', (e) => {
  e.preventDefault();           // Cancel default action
  e.stopPropagation();          // Stop bubbling to parent elements
  e.stopImmediatePropagation(); // Stop other listeners on same element
  return false;                 // Extra safety
}, true);  // ← Capture phase = intercept FIRST
```

### Plus CSS Protection:
```css
* {
  user-select: none !important;
  -webkit-user-drag: none !important;
}
```

---

## 📝 Report Results

After testing, tell me:
1. ✅ Which tests PASSED (action was blocked)
2. ❌ Which tests FAILED (action still worked)
3. 📋 Show me: `SELECT type, COUNT(*) FROM events WHERE student_id=7 GROUP BY type`

---

## 🚨 JWT Token Refresh Reminder

If you see these errors:
```
HTTP 401 Unauthorized
JWT signature does not match locally computed signature
```

**Solution:** Get a fresh token! Run:
```powershell
.\get-fresh-token.ps1
```

Then save it in the extension popup again.

---

## ✨ What Changed in Latest Code

1. **content.js line 14**: Added `applySelectionBlockingCSS()` on monitoring start
2. **content.js line 21**: Added `removeSelectionBlockingCSS()` on monitoring stop
3. **content.js line 30**: New function to inject CSS that disables selection
4. **content.js line 118**: Added `, true` to copy event listener
5. **content.js line 131**: Added `, true` to paste event listener
6. **content.js line 144**: Added `, true` to contextmenu event listener
7. **content.js line 213**: Added `, true` to keydown event listener

All event listeners now run in **capture phase** with **triple prevention**!
