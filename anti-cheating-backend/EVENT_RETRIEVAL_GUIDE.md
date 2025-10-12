# Event Retrieval Testing Guide

## Problem
Getting 200 OK but no events are returned when calling `/api/events/student/{studentId}`

## Possible Causes

1. **No events exist in the database for that student**
2. **Wrong date format or timezone issues**
3. **Date range doesn't include the events**
4. **Wrong studentId**
5. **ADMIN role required but user has STUDENT role**

## Step-by-Step Testing

### Step 1: Check if ANY events exist for the student

**New endpoint added:** `GET /api/events/student/{studentId}/all`

This endpoint returns ALL events for a student (no date filtering).

**Postman Request:**
- Method: `GET`
- URL: `http://localhost:8080/api/events/student/1/all`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_OR_STUDENT_TOKEN`

**Expected Response:**
```json
{
  "studentId": 1,
  "eventCount": 5,
  "events": [
    {
      "id": 1,
      "type": "TAB_SWITCH",
      "timestamp": "2025-10-12T10:30:00",
      "details": "...",
      ...
    }
  ]
}
```

**If eventCount is 0:** No events exist in database for this student. You need to log some events first!

### Step 2: Log some test events

**Create events first:**

```powershell
$token = "YOUR_STUDENT_TOKEN"

# Log event 1
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "type=TAB_SWITCH" `
  -F "details=Test tab switch"

# Log event 2
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "type=COPY" `
  -F "details=Test copy"

# Log event 3
curl.exe -X POST http://localhost:8080/api/events/log `
  -H "Authorization: Bearer $token" `
  -F "studentId=1" `
  -F "type=PASTE" `
  -F "details=Test paste"
```

### Step 3: Test the original endpoint with correct date format

The original endpoint requires date range parameters.

**Postman Request:**
- Method: `GET`
- URL: `http://localhost:8080/api/events/student/1`
- Headers:
  - `Authorization: Bearer YOUR_ADMIN_TOKEN` (⚠️ Must be ADMIN role!)
- Query Parameters:
  - `startTime`: `2025-10-12T00:00:00`
  - `endTime`: `2025-10-12T23:59:59`

**Full URL Example:**
```
http://localhost:8080/api/events/student/1?startTime=2025-10-12T00:00:00&endTime=2025-10-12T23:59:59
```

## Date Format Requirements

The date must be in ISO-8601 format: `yyyy-MM-ddTHH:mm:ss`

**Examples:**
- ✅ `2025-10-12T00:00:00` (Correct)
- ✅ `2025-10-12T10:30:00` (Correct)
- ❌ `2025-10-12` (Wrong - missing time)
- ❌ `10/12/2025` (Wrong - wrong format)
- ❌ `2025-10-12 00:00:00` (Wrong - space instead of T)

## Common Issues and Solutions

### Issue 1: Getting Empty Array `[]`

**Causes:**
1. No events exist for that student
2. Date range doesn't include the event timestamps
3. Timezone mismatch

**Solution:**
- First, use the `/all` endpoint to check if events exist
- Check the actual timestamps of your events
- Use a wide date range (e.g., whole month)

### Issue 2: 401 Unauthorized

**Cause:** Missing or invalid token

**Solution:**
- Make sure you're logged in
- Include `Authorization: Bearer YOUR_TOKEN` header
- Token must not be expired

### Issue 3: 403 Forbidden

**Cause:** Wrong role - endpoint requires ADMIN but you're using STUDENT token

**Solution for original endpoint:**
- Use an ADMIN token
- Or change the annotation to:
  ```java
  @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
  ```

**Solution for new `/all` endpoint:**
- Already allows both ADMIN and STUDENT roles

### Issue 4: Date parsing error

**Error:** `Failed to convert value of type 'java.lang.String' to required type 'java.time.LocalDateTime'`

**Cause:** Wrong date format

**Solution:** Use ISO-8601 format: `2025-10-12T00:00:00`

## Testing Strategy

### Test 1: Verify events exist
```
GET /api/events/student/1/all
Authorization: Bearer YOUR_TOKEN
```

If this returns events, move to Test 2. If not, log some events first.

### Test 2: Test with wide date range
```
GET /api/events/student/1?startTime=2025-01-01T00:00:00&endTime=2025-12-31T23:59:59
Authorization: Bearer YOUR_ADMIN_TOKEN
```

### Test 3: Test with specific date
Look at the timestamp of an event from Test 1, then query for that specific date:
```
GET /api/events/student/1?startTime=2025-10-12T00:00:00&endTime=2025-10-12T23:59:59
```

## Backend Logs

After the fix, when you call the endpoint, check your backend console for logs:

```
Fetching events for studentId: 1
Start time: 2025-10-12T00:00:00
End time: 2025-10-12T23:59:59
Found 3 events
```

This will tell you exactly what's being queried and how many results were found.

## Database Check

If you want to check directly in the database:

```sql
-- Check all events
SELECT * FROM events;

-- Check events for specific student
SELECT * FROM events WHERE student_id = 1;

-- Check with date range
SELECT * FROM events 
WHERE student_id = 1 
AND timestamp BETWEEN '2025-10-12 00:00:00' AND '2025-10-12 23:59:59';
```

## Quick Test Commands

### Using cURL:

```powershell
# Test 1: Get all events (no date filter)
$adminToken = "YOUR_ADMIN_TOKEN"
curl.exe -X GET "http://localhost:8080/api/events/student/1/all" `
  -H "Authorization: Bearer $adminToken"

# Test 2: Get events with date range
curl.exe -X GET "http://localhost:8080/api/events/student/1?startTime=2025-10-12T00:00:00&endTime=2025-10-12T23:59:59" `
  -H "Authorization: Bearer $adminToken"
```

### Using PowerShell Invoke-RestMethod:

```powershell
$headers = @{
    "Authorization" = "Bearer YOUR_ADMIN_TOKEN"
}

# Test 1: All events
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/events/student/1/all" -Headers $headers -Method Get
$response | ConvertTo-Json -Depth 5

# Test 2: With date range
$params = @{
    startTime = "2025-10-12T00:00:00"
    endTime = "2025-10-12T23:59:59"
}
$queryString = ($params.GetEnumerator() | ForEach-Object { "$($_.Key)=$($_.Value)" }) -join "&"
$response = Invoke-RestMethod -Uri "http://localhost:8080/api/events/student/1?$queryString" -Headers $headers -Method Get
$response | ConvertTo-Json -Depth 5
```

## Summary

1. **Use the new `/all` endpoint first** to verify events exist
2. **Make sure you're using an ADMIN token** for the original endpoint
3. **Use correct date format:** `yyyy-MM-ddTHH:mm:ss`
4. **Check backend logs** to see what's being queried
5. **If no events found,** log some test events first

---

**The debugging logs are now in place! Check your backend console when you make the request.** 🔍
