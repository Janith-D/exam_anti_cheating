# Quick Verification Test
# This script tests the enrollment endpoint after the role fix

Write-Host "=== Testing Enrollment Endpoint After Role Fix ===" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"

Write-Host "⚠️  IMPORTANT: You must restart the backend for this fix to work!" -ForegroundColor Yellow
Write-Host "   Press Ctrl+C in the backend terminal, then run: .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
Write-Host ""
$restart = Read-Host "Have you restarted the backend? (y/n)"

if ($restart -ne "y" -and $restart -ne "Y") {
    Write-Host ""
    Write-Host "Please restart the backend first, then run this script again." -ForegroundColor Red
    exit
}

Write-Host ""
$imagePath = Read-Host "Enter path to a test image (jpg/png)"

if (-not (Test-Path $imagePath)) {
    Write-Host "✗ Image file not found!" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Testing..." -ForegroundColor Yellow
Write-Host ""

# Generate unique credentials
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$userName = "student$timestamp"
$password = "TestPass123!"
$email = "student$timestamp@test.com"
$studentId = "STU$timestamp"

# Step 1: Register
Write-Host "1️⃣  Registering user: $userName" -ForegroundColor Cyan
$regResponse = curl.exe -s -X POST "$baseUrl/api/auth/register" `
    -F "userName=$userName" `
    -F "password=$password" `
    -F "email=$email" `
    -F "firstName=Test" `
    -F "lastName=Student" `
    -F "role=STUDENT" `
    -F "studentId=$studentId" `
    -F "image=@$imagePath"

Write-Host "   Response: $regResponse" -ForegroundColor Gray

try {
    $regJson = $regResponse | ConvertFrom-Json
    if ($regJson.userId) {
        Write-Host "   ✅ Registered! User ID: $($regJson.userId)" -ForegroundColor Green
        $userId = $regJson.userId
    } else {
        Write-Host "   ❌ Registration failed" -ForegroundColor Red
        exit
    }
} catch {
    Write-Host "   ❌ Registration failed - invalid response" -ForegroundColor Red
    exit
}

Write-Host ""

# Step 2: Login
Write-Host "2️⃣  Logging in to get JWT token..." -ForegroundColor Cyan
$loginResponse = curl.exe -s -X POST "$baseUrl/api/auth/login" `
    -F "userName=$userName" `
    -F "password=$password" `
    -F "image=@$imagePath"

try {
    $loginJson = $loginResponse | ConvertFrom-Json
    if ($loginJson.token) {
        Write-Host "   ✅ Login successful!" -ForegroundColor Green
        Write-Host "   Token: $($loginJson.token.Substring(0, 40))..." -ForegroundColor Gray
        Write-Host "   Role: $($loginJson.role)" -ForegroundColor Gray
        $token = $loginJson.token
    } else {
        Write-Host "   ❌ Login failed" -ForegroundColor Red
        Write-Host "   Response: $loginResponse" -ForegroundColor Gray
        exit
    }
} catch {
    Write-Host "   ❌ Login failed - invalid response" -ForegroundColor Red
    exit
}

Write-Host ""

# Step 3: Test enrollment endpoint
Write-Host "3️⃣  Testing enrollment endpoint with Bearer token..." -ForegroundColor Cyan
Write-Host "   URL: POST $baseUrl/api/enrollment/enroll" -ForegroundColor Gray
Write-Host "   Header: Authorization: Bearer <token>" -ForegroundColor Gray
Write-Host "   Body: studentId=$userId, image=<file>" -ForegroundColor Gray
Write-Host ""

$enrollResponse = curl.exe -s -w "`nHTTP_STATUS:%{http_code}" -X POST "$baseUrl/api/enrollment/enroll" `
    -H "Authorization: Bearer $token" `
    -F "studentId=$userId" `
    -F "image=@$imagePath"

Write-Host "   Full Response:" -ForegroundColor Gray
Write-Host "   $enrollResponse" -ForegroundColor Gray
Write-Host ""

# Check HTTP status
if ($enrollResponse -match "HTTP_STATUS:200" -or $enrollResponse -match "HTTP_STATUS:201") {
    Write-Host "   ✅✅✅ SUCCESS! Enrollment endpoint is now working!" -ForegroundColor Green
    Write-Host ""
    Write-Host "   The issue is FIXED! 🎉" -ForegroundColor Green
} elseif ($enrollResponse -match "HTTP_STATUS:401") {
    Write-Host "   ❌ Still getting 401 Unauthorized" -ForegroundColor Red
    Write-Host ""
    Write-Host "   Possible causes:" -ForegroundColor Yellow
    Write-Host "   1. Backend was not restarted after the code fix" -ForegroundColor White
    Write-Host "   2. Check backend logs for JWT validation errors" -ForegroundColor White
    Write-Host "   3. The token might be malformed" -ForegroundColor White
    Write-Host ""
    Write-Host "   Backend logs should show:" -ForegroundColor Yellow
    Write-Host "   'JWT Filter - Authentication successful for user: $userName with roles: [ROLE_STUDENT]'" -ForegroundColor Gray
} elseif ($enrollResponse -match "HTTP_STATUS:403") {
    Write-Host "   ⚠️  Getting 403 Forbidden (different from 401)" -ForegroundColor Yellow
    Write-Host "   This means authentication worked but authorization failed" -ForegroundColor White
    Write-Host "   Check that the role is properly prefixed with 'ROLE_'" -ForegroundColor White
} else {
    Write-Host "   ⚠️  Got unexpected response" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Your test credentials:" -ForegroundColor White
Write-Host "  Username: $userName" -ForegroundColor Gray
Write-Host "  Password: $password" -ForegroundColor Gray
Write-Host "  User ID: $userId" -ForegroundColor Gray
Write-Host "  Token: $token" -ForegroundColor Gray
Write-Host ""
Write-Host "Check your backend logs to see the JWT Filter messages!" -ForegroundColor Yellow
