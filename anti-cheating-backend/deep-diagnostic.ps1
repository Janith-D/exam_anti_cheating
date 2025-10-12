# DEEP DIAGNOSTIC - Find the Root Cause
# This script checks EVERY component in the authentication flow

Write-Host "╔═══════════════════════════════════════════════════════════════╗" -ForegroundColor Cyan
Write-Host "║     DEEP DIAGNOSTIC - ENROLLMENT ENDPOINT 401 ERROR          ║" -ForegroundColor Cyan
Write-Host "╚═══════════════════════════════════════════════════════════════╝" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"

# ============================================================================
# STEP 1: CHECK BACKEND STATUS
# ============================================================================
Write-Host "═══ STEP 1: Backend Health Check ═══" -ForegroundColor Yellow
Write-Host ""

try {
    $healthResponse = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
    Write-Host "✅ Backend is running" -ForegroundColor Green
    Write-Host "   Status: $($healthResponse.status)" -ForegroundColor Gray
} catch {
    Write-Host "❌ Backend is NOT running!" -ForegroundColor Red
    Write-Host "   Start it with: .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
    exit
}

Write-Host ""

# ============================================================================
# STEP 2: CHECK BACKEND LOGS ARE ENABLED
# ============================================================================
Write-Host "═══ STEP 2: Verify Logging Configuration ═══" -ForegroundColor Yellow
Write-Host ""
Write-Host "⚠️  IMPORTANT: Check your backend terminal now!" -ForegroundColor Yellow
Write-Host "   You should see detailed log messages like:" -ForegroundColor White
Write-Host "   - 'JWT Filter - Request URI: ...'" -ForegroundColor Gray
Write-Host "   - 'JWT Filter - Authorization Header: ...'" -ForegroundColor Gray
Write-Host ""
Write-Host "   If you DON'T see these logs, the logging was not applied." -ForegroundColor Yellow
Write-Host "   You need to RESTART the backend!" -ForegroundColor Yellow
Write-Host ""
$logsVisible = Read-Host "Can you see detailed JWT Filter logs in backend? (y/n)"

if ($logsVisible -ne "y" -and $logsVisible -ne "Y") {
    Write-Host ""
    Write-Host "❌ ISSUE FOUND: Backend needs to be restarted!" -ForegroundColor Red
    Write-Host ""
    Write-Host "ACTION REQUIRED:" -ForegroundColor Yellow
    Write-Host "1. Stop the backend (Ctrl+C)" -ForegroundColor White
    Write-Host "2. Start it again: .\mvnw.cmd spring-boot:run" -ForegroundColor White
    Write-Host "3. Wait for it to fully start" -ForegroundColor White
    Write-Host "4. Run this script again" -ForegroundColor White
    Write-Host ""
    exit
}

Write-Host "✅ Logs are visible - proceeding..." -ForegroundColor Green
Write-Host ""

# ============================================================================
# STEP 3: GET TEST IMAGE
# ============================================================================
Write-Host "═══ STEP 3: Image File Setup ═══" -ForegroundColor Yellow
Write-Host ""

$imagePath = Read-Host "Enter path to test image"

if (-not (Test-Path $imagePath)) {
    Write-Host "❌ Image file not found!" -ForegroundColor Red
    exit
}

$imageSize = (Get-Item $imagePath).Length / 1KB
Write-Host "✅ Image found: $imagePath ($([math]::Round($imageSize, 2)) KB)" -ForegroundColor Green
Write-Host ""

# ============================================================================
# STEP 4: TEST REGISTRATION
# ============================================================================
Write-Host "═══ STEP 4: Testing Registration ═══" -ForegroundColor Yellow
Write-Host ""

$userName = "diagnostic$timestamp"
$password = "DiagPass123!"
$email = "diag$timestamp@test.com"
$studentId = "DIAG$timestamp"

Write-Host "Creating user:" -ForegroundColor White
Write-Host "  Username: $userName" -ForegroundColor Gray
Write-Host "  Email: $email" -ForegroundColor Gray
Write-Host "  Student ID: $studentId" -ForegroundColor Gray
Write-Host "  Role: STUDENT" -ForegroundColor Gray
Write-Host ""

Write-Host "Sending registration request..." -ForegroundColor White
$regStart = Get-Date

$regResponse = curl.exe -s -w "`n###STATUS###%{http_code}###" -X POST "$baseUrl/api/auth/register" `
    -F "userName=$userName" `
    -F "password=$password" `
    -F "email=$email" `
    -F "firstName=Diag" `
    -F "lastName=Test" `
    -F "role=STUDENT" `
    -F "studentId=$studentId" `
    -F "image=@$imagePath"

$regDuration = ((Get-Date) - $regStart).TotalSeconds

# Parse response
$regParts = $regResponse -split "###STATUS###"
$regBody = $regParts[0]
$regStatus = $regParts[1]

Write-Host "Response time: $([math]::Round($regDuration, 2))s" -ForegroundColor Gray
Write-Host "HTTP Status: $regStatus" -ForegroundColor Gray
Write-Host "Response body: $regBody" -ForegroundColor Gray
Write-Host ""

if ($regStatus -eq "200") {
    try {
        $regJson = $regBody | ConvertFrom-Json
        if ($regJson.userId) {
            Write-Host "✅ Registration successful!" -ForegroundColor Green
            Write-Host "   User ID: $($regJson.userId)" -ForegroundColor Gray
            Write-Host "   Enrollment ID: $($regJson.enrollmentId)" -ForegroundColor Gray
            $userId = $regJson.userId
        } else {
            Write-Host "⚠️  Registration returned 200 but no userId in response" -ForegroundColor Yellow
            Write-Host "   Response: $regBody" -ForegroundColor Gray
            exit
        }
    } catch {
        Write-Host "❌ Failed to parse registration response" -ForegroundColor Red
        exit
    }
} else {
    Write-Host "❌ Registration failed with status $regStatus" -ForegroundColor Red
    Write-Host "   Error: $regBody" -ForegroundColor Gray
    exit
}

Write-Host ""

# ============================================================================
# STEP 5: TEST LOGIN AND ANALYZE TOKEN
# ============================================================================
Write-Host "═══ STEP 5: Testing Login & JWT Token Generation ═══" -ForegroundColor Yellow
Write-Host ""

Write-Host "Sending login request..." -ForegroundColor White
$loginStart = Get-Date

$loginResponse = curl.exe -s -w "`n###STATUS###%{http_code}###" -X POST "$baseUrl/api/auth/login" `
    -F "userName=$userName" `
    -F "password=$password" `
    -F "image=@$imagePath"

$loginDuration = ((Get-Date) - $loginStart).TotalSeconds

# Parse response
$loginParts = $loginResponse -split "###STATUS###"
$loginBody = $loginParts[0]
$loginStatus = $loginParts[1]

Write-Host "Response time: $([math]::Round($loginDuration, 2))s" -ForegroundColor Gray
Write-Host "HTTP Status: $loginStatus" -ForegroundColor Gray
Write-Host ""

if ($loginStatus -eq "200") {
    try {
        $loginJson = $loginBody | ConvertFrom-Json
        if ($loginJson.token) {
            Write-Host "✅ Login successful!" -ForegroundColor Green
            Write-Host "   Username: $($loginJson.userName)" -ForegroundColor Gray
            Write-Host "   Role from response: $($loginJson.role)" -ForegroundColor Gray
            $token = $loginJson.token
            
            # Analyze token
            Write-Host ""
            Write-Host "🔍 Analyzing JWT Token:" -ForegroundColor Cyan
            Write-Host "   Token length: $($token.Length) chars" -ForegroundColor Gray
            Write-Host "   Token (first 50): $($token.Substring(0, [Math]::Min(50, $token.Length)))..." -ForegroundColor Gray
            
            # Decode token payload
            $tokenParts = $token -split "\."
            if ($tokenParts.Count -eq 3) {
                Write-Host "   ✅ Token structure: Valid (3 parts)" -ForegroundColor Green
                
                try {
                    $payload = $tokenParts[1]
                    # Fix Base64 padding
                    while ($payload.Length % 4 -ne 0) { $payload += "=" }
                    $payloadBytes = [Convert]::FromBase64String($payload)
                    $payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
                    $payloadObj = $payloadJson | ConvertFrom-Json
                    
                    Write-Host "   Token subject (username): $($payloadObj.sub)" -ForegroundColor Gray
                    if ($payloadObj.exp) {
                        $expDate = [DateTimeOffset]::FromUnixTimeSeconds($payloadObj.exp).LocalDateTime
                        Write-Host "   Token expiration: $expDate" -ForegroundColor Gray
                        if ($expDate -lt (Get-Date)) {
                            Write-Host "   ❌ TOKEN IS EXPIRED!" -ForegroundColor Red
                        } else {
                            $timeLeft = $expDate - (Get-Date)
                            Write-Host "   ✅ Token valid for: $([math]::Round($timeLeft.TotalHours, 1)) hours" -ForegroundColor Green
                        }
                    }
                } catch {
                    Write-Host "   ⚠️  Could not decode token payload" -ForegroundColor Yellow
                }
            } else {
                Write-Host "   ❌ Token structure: INVALID (should have 3 parts, has $($tokenParts.Count))" -ForegroundColor Red
            }
        } else {
            Write-Host "❌ Login succeeded but no token in response!" -ForegroundColor Red
            exit
        }
    } catch {
        Write-Host "❌ Failed to parse login response" -ForegroundColor Red
        Write-Host "   Raw response: $loginBody" -ForegroundColor Gray
        exit
    }
} else {
    Write-Host "❌ Login failed with status $loginStatus" -ForegroundColor Red
    Write-Host "   Error: $loginBody" -ForegroundColor Gray
    exit
}

Write-Host ""

# ============================================================================
# STEP 6: TEST ENROLLMENT ENDPOINT - DETAILED
# ============================================================================
Write-Host "═══ STEP 6: Testing Enrollment Endpoint (THE MAIN TEST) ═══" -ForegroundColor Yellow
Write-Host ""

Write-Host "Request details:" -ForegroundColor White
Write-Host "  URL: POST $baseUrl/api/enrollment/enroll" -ForegroundColor Gray
Write-Host "  Header: Authorization: Bearer <token>" -ForegroundColor Gray
Write-Host "  Body: studentId=$userId, image=<file>" -ForegroundColor Gray
Write-Host ""

Write-Host "⚠️  WATCH YOUR BACKEND LOGS NOW!" -ForegroundColor Yellow
Write-Host "   You should see:" -ForegroundColor White
Write-Host "   - 'JWT Filter - Request URI: /api/enrollment/enroll'" -ForegroundColor Gray
Write-Host "   - 'JWT Filter - Authorization Header: Present'" -ForegroundColor Gray
Write-Host "   - 'JWT Filter - Extracted username: $userName'" -ForegroundColor Gray
Write-Host "   - 'Loading user: $userName with role: STUDENT'" -ForegroundColor Gray
Write-Host "   - 'JWT Filter - Authentication successful for user: $userName with roles: [ROLE_STUDENT]'" -ForegroundColor Gray
Write-Host ""

Write-Host "Sending enrollment request..." -ForegroundColor White
$enrollStart = Get-Date

# Use verbose curl to capture all details
$enrollResponse = curl.exe -v -s -X POST "$baseUrl/api/enrollment/enroll" `
    -H "Authorization: Bearer $token" `
    -F "studentId=$userId" `
    -F "image=@$imagePath" 2>&1

$enrollDuration = ((Get-Date) - $enrollStart).TotalSeconds

Write-Host ""
Write-Host "Response time: $([math]::Round($enrollDuration, 2))s" -ForegroundColor Gray
Write-Host ""
Write-Host "Full curl output:" -ForegroundColor White
Write-Host "----------------------------------------" -ForegroundColor Gray
Write-Host $enrollResponse -ForegroundColor Gray
Write-Host "----------------------------------------" -ForegroundColor Gray
Write-Host ""

# ============================================================================
# STEP 7: ANALYZE RESULT
# ============================================================================
Write-Host "═══ STEP 7: Result Analysis ═══" -ForegroundColor Yellow
Write-Host ""

if ($enrollResponse -match "HTTP/[\d\.]+ 200" -or $enrollResponse -match "HTTP/[\d\.]+ 201") {
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Green
    Write-Host "║                    ✅ SUCCESS! ✅                          ║" -ForegroundColor Green
    Write-Host "║   The enrollment endpoint is working correctly!          ║" -ForegroundColor Green
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Green
    Write-Host ""
    Write-Host "The fix is working! You can now:" -ForegroundColor White
    Write-Host "  1. Use the enrollment endpoint with proper JWT auth" -ForegroundColor Gray
    Write-Host "  2. All authenticated endpoints should work" -ForegroundColor Gray
    
} elseif ($enrollResponse -match "HTTP/[\d\.]+ 401") {
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Red
    Write-Host "║                 ❌ STILL 401 UNAUTHORIZED ❌               ║" -ForegroundColor Red
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Red
    Write-Host ""
    Write-Host "DIAGNOSTIC FINDINGS:" -ForegroundColor Yellow
    Write-Host ""
    
    # Check what went wrong
    Write-Host "Checking backend logs..." -ForegroundColor White
    Write-Host ""
    Write-Host "In your backend terminal, check for:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "1️⃣  If you see 'JWT Filter - Authorization Header: Missing':" -ForegroundColor White
    Write-Host "   → The Authorization header is not being sent/received" -ForegroundColor Gray
    Write-Host "   → Issue: HTTP request formatting problem" -ForegroundColor Gray
    Write-Host ""
    Write-Host "2️⃣  If you see 'JWT Filter - Failed to extract username':" -ForegroundColor White
    Write-Host "   → The token is malformed or invalid" -ForegroundColor Gray
    Write-Host "   → Issue: Token generation problem" -ForegroundColor Gray
    Write-Host ""
    Write-Host "3️⃣  If you see 'JWT Filter - Token validation failed':" -ForegroundColor White
    Write-Host "   → Token doesn't match the user or is expired" -ForegroundColor Gray
    Write-Host "   → Issue: Token validation problem" -ForegroundColor Gray
    Write-Host ""
    Write-Host "4️⃣  If you see 'Authentication successful...with roles: [STUDENT]':" -ForegroundColor White
    Write-Host "   → The role is MISSING the 'ROLE_' prefix!" -ForegroundColor Gray
    Write-Host "   → Issue: Code changes were not applied - RESTART NEEDED!" -ForegroundColor Gray
    Write-Host ""
    Write-Host "5️⃣  If you see 'Authentication successful...with roles: [ROLE_STUDENT]':" -ForegroundColor White
    Write-Host "   → JWT auth worked, but @PreAuthorize still blocked it" -ForegroundColor Gray
    Write-Host "   → Issue: @EnableMethodSecurity configuration problem" -ForegroundColor Gray
    Write-Host ""
    
    Write-Host "MOST LIKELY CAUSE:" -ForegroundColor Red
    Write-Host "  The backend was NOT restarted after code changes!" -ForegroundColor White
    Write-Host ""
    Write-Host "ACTION REQUIRED:" -ForegroundColor Yellow
    Write-Host "  1. Look at backend terminal RIGHT NOW" -ForegroundColor White
    Write-Host "  2. Find the log line: 'JWT Filter - Authentication successful...'" -ForegroundColor White
    Write-Host "  3. Check if it says 'roles: [ROLE_STUDENT]' or 'roles: [STUDENT]'" -ForegroundColor White
    Write-Host ""
    Write-Host "  If it says [STUDENT] without ROLE_ prefix:" -ForegroundColor Yellow
    Write-Host "  → STOP the backend (Ctrl+C)" -ForegroundColor White
    Write-Host "  → START it again: .\mvnw.cmd spring-boot:run" -ForegroundColor White
    Write-Host "  → RUN this script again after restart" -ForegroundColor White
    
} elseif ($enrollResponse -match "HTTP/[\d\.]+ 403") {
    Write-Host "╔═══════════════════════════════════════════════════════════╗" -ForegroundColor Yellow
    Write-Host "║                   ⚠️  403 FORBIDDEN ⚠️                     ║" -ForegroundColor Yellow
    Write-Host "╚═══════════════════════════════════════════════════════════╝" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "DIAGNOSTIC: 403 means authentication worked, but authorization failed" -ForegroundColor White
    Write-Host ""
    Write-Host "Check backend logs for:" -ForegroundColor Yellow
    Write-Host "  'JWT Filter - Authentication successful...with roles: [???]'" -ForegroundColor Gray
    Write-Host ""
    Write-Host "If it shows [STUDENT]:" -ForegroundColor White
    Write-Host "  → Missing ROLE_ prefix - restart backend!" -ForegroundColor Red
    Write-Host ""
    Write-Host "If it shows [ROLE_STUDENT]:" -ForegroundColor White
    Write-Host "  → Role is correct, but @PreAuthorize check still failing" -ForegroundColor Yellow
    Write-Host "  → Check @EnableMethodSecurity annotation in SecurityConfig" -ForegroundColor Yellow
    
} else {
    Write-Host "⚠️  Got unexpected HTTP status" -ForegroundColor Yellow
    Write-Host "   Check the curl output above for details" -ForegroundColor White
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host ""
Write-Host "Test credentials (save these):" -ForegroundColor White
Write-Host "  Username: $userName" -ForegroundColor Gray
Write-Host "  Password: $password" -ForegroundColor Gray
Write-Host "  User ID: $userId" -ForegroundColor Gray
Write-Host "  Token: $token" -ForegroundColor Gray
Write-Host ""
Write-Host "NEXT STEP: Check your backend logs for the JWT Filter messages!" -ForegroundColor Yellow
Write-Host ""
