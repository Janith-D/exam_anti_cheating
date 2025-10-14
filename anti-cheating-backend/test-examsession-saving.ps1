# Test if examSessionId is being saved to database

Write-Host "🔍 Testing examSessionId saving to database..." -ForegroundColor Cyan
Write-Host ""

# Step 1: Register a test student
Write-Host "Step 1: Registering test student..." -ForegroundColor Yellow
$registerBody = @{
    username = "examtest_" + (Get-Date -Format "yyyyMMddHHmmss")
    email = "examtest@test.com"
    password = "test123"
    role = "STUDENT"
} | ConvertTo-Json

try {
    $student = Invoke-RestMethod -Uri "http://localhost:8080/api/auth/register" `
        -Method Post `
        -Body $registerBody `
        -ContentType "application/json"
    
    Write-Host "✅ Student registered!" -ForegroundColor Green
    Write-Host "   Student ID: $($student.userId)" -ForegroundColor White
    Write-Host "   Token: $($student.token.Substring(0, 50))..." -ForegroundColor White
    
    $token = $student.token
    $studentId = $student.userId
    
} catch {
    Write-Host "❌ Failed to register student: $_" -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 2: Create an exam session (or use existing)
Write-Host "Step 2: Checking exam sessions..." -ForegroundColor Yellow

$headers = @{
    "Authorization" = "Bearer $token"
}

try {
    # Try to get existing exam sessions
    $examSessions = Invoke-RestMethod -Uri "http://localhost:8080/api/examsessions" `
        -Method Get `
        -Headers $headers
    
    if ($examSessions -and $examSessions.Count -gt 0) {
        $examSessionId = $examSessions[0].id
        Write-Host "✅ Using existing exam session ID: $examSessionId" -ForegroundColor Green
    } else {
        Write-Host "⚠️ No exam sessions found, using ID: 1" -ForegroundColor Yellow
        $examSessionId = 1
    }
} catch {
    Write-Host "⚠️ Could not fetch exam sessions, using ID: 1" -ForegroundColor Yellow
    $examSessionId = 1
}

Write-Host ""

# Step 3: Log an event WITH examSessionId
Write-Host "Step 3: Logging event WITH examSessionId..." -ForegroundColor Yellow

# Create multipart form data
$boundary = [System.Guid]::NewGuid().ToString()
$LF = "`r`n"

$bodyLines = ( 
    "--$boundary",
    "Content-Disposition: form-data; name=`"studentId`"$LF",
    "$studentId",
    "--$boundary",
    "Content-Disposition: form-data; name=`"type`"$LF",
    "TEST_EVENT",
    "--$boundary",
    "Content-Disposition: form-data; name=`"details`"$LF",
    "Testing examSessionId saving",
    "--$boundary",
    "Content-Disposition: form-data; name=`"examSessionId`"$LF",
    "$examSessionId",
    "--$boundary--$LF"
) -join $LF

try {
    $response = Invoke-RestMethod -Uri "http://localhost:8080/api/events/log" `
        -Method Post `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "multipart/form-data; boundary=$boundary"
        } `
        -Body $bodyLines
    
    Write-Host "✅ Event logged!" -ForegroundColor Green
    Write-Host "   Event ID: $($response.eventId)" -ForegroundColor White
    $eventId = $response.eventId
    
} catch {
    Write-Host "❌ Failed to log event: $_" -ForegroundColor Red
    Write-Host $_.Exception.Response.StatusCode -ForegroundColor Red
    exit 1
}

Write-Host ""

# Step 4: Verify in database
Write-Host "Step 4: Verifying in database..." -ForegroundColor Yellow
Write-Host ""
Write-Host "Run this SQL query to check if examSessionId was saved:" -ForegroundColor Cyan
Write-Host ""
Write-Host "SELECT id, student_id, exam_session_id, type, details, timestamp" -ForegroundColor White
Write-Host "FROM events" -ForegroundColor White
Write-Host "WHERE id = $eventId;" -ForegroundColor White
Write-Host ""
Write-Host "Expected Result:" -ForegroundColor Yellow
Write-Host "  - id: $eventId" -ForegroundColor White
Write-Host "  - student_id: $studentId" -ForegroundColor White
Write-Host "  - exam_session_id: $examSessionId  ← Should NOT be NULL!" -ForegroundColor Green
Write-Host "  - type: TEST_EVENT" -ForegroundColor White
Write-Host ""

# Step 5: Get events for the student
Write-Host "Step 5: Fetching events via API..." -ForegroundColor Yellow

try {
    $events = Invoke-RestMethod -Uri "http://localhost:8080/api/events/student/$studentId" `
        -Method Get `
        -Headers $headers
    
    Write-Host "✅ Retrieved $($events.Count) event(s)" -ForegroundColor Green
    
    foreach ($event in $events) {
        Write-Host ""
        Write-Host "Event Details:" -ForegroundColor Cyan
        Write-Host "  - Event ID: $($event.id)" -ForegroundColor White
        Write-Host "  - Type: $($event.type)" -ForegroundColor White
        Write-Host "  - Details: $($event.details)" -ForegroundColor White
        Write-Host "  - Student ID: $($event.student.id)" -ForegroundColor White
        
        if ($event.examSession) {
            Write-Host "  - Exam Session ID: $($event.examSession.id) ✅" -ForegroundColor Green
            Write-Host "     examSessionId IS SAVED!" -ForegroundColor Green
        } else {
            Write-Host "  - Exam Session ID: NULL ❌" -ForegroundColor Red
            Write-Host "     examSessionId is NOT being saved!" -ForegroundColor Red
        }
    }
} catch {
    Write-Host "❌ Failed to fetch events: $_" -ForegroundColor Red
}

Write-Host ""
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "Test completed!" -ForegroundColor Green
Write-Host ""
Write-Host "Summary:" -ForegroundColor Yellow
Write-Host "  - Student ID: $studentId" -ForegroundColor White
Write-Host "  - Event ID: $eventId" -ForegroundColor White
Write-Host "  - Expected Exam Session ID: $examSessionId" -ForegroundColor White
Write-Host ""
Write-Host "Check the SQL query result to verify examSessionId was saved!" -ForegroundColor Cyan
