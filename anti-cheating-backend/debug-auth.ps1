# Simple Debug Test - Check Each Step
Write-Host "=== Debugging Unauthorized Error ===" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"

# Test 1: Check if backend is running
Write-Host "Test 1: Checking if backend is running..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/health" -Method Get
    Write-Host "✓ Backend is running - Status Code: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "✗ Backend is NOT running!" -ForegroundColor Red
    Write-Host "  Please start the backend with: .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
    exit
}

Write-Host ""
Write-Host "Test 2: Testing enrollment endpoint WITHOUT token (should fail)..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "$baseUrl/api/enrollment/enroll" -Method Post
    Write-Host "✗ Unexpected: Got response without auth" -ForegroundColor Red
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 401) {
        Write-Host "✓ Correctly returns 401 Unauthorized (as expected)" -ForegroundColor Green
    } else {
        Write-Host "! Got status code: $($_.Exception.Response.StatusCode.value__)" -ForegroundColor Yellow
    }
}

Write-Host ""
Write-Host "=== Next Steps ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "The endpoint is working correctly - it requires authentication." -ForegroundColor White
Write-Host ""
Write-Host "To access the enrollment endpoint, you MUST:" -ForegroundColor Yellow
Write-Host "  1. Register a user (POST /api/auth/register)" -ForegroundColor White
Write-Host "  2. Login (POST /api/auth/login) to get a JWT token" -ForegroundColor White
Write-Host "  3. Include the token in your request:" -ForegroundColor White
Write-Host "     Header: Authorization: Bearer <your-token>" -ForegroundColor Gray
Write-Host ""
Write-Host "Do you want to run a full test with registration and login? (y/n)" -ForegroundColor Yellow
$answer = Read-Host

if ($answer -eq "y" -or $answer -eq "Y") {
    Write-Host ""
    Write-Host "Please provide an image file path for testing:" -ForegroundColor Yellow
    $imagePath = Read-Host "Image path"
    
    if (-not (Test-Path $imagePath)) {
        Write-Host "✗ Image file not found!" -ForegroundColor Red
        exit
    }
    
    Write-Host ""
    Write-Host "=== Running Full Authentication Flow ===" -ForegroundColor Cyan
    
    # Generate unique username
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $userName = "testuser$timestamp"
    $password = "TestPass123!"
    $email = "test$timestamp@example.com"
    
    Write-Host ""
    Write-Host "Step 1: Registering user '$userName'..." -ForegroundColor Yellow
    
    # Using curl.exe for simpler multipart form data
    $registerOutput = curl.exe -s -X POST "$baseUrl/api/auth/register" `
        -F "userName=$userName" `
        -F "password=$password" `
        -F "email=$email" `
        -F "firstName=Test" `
        -F "lastName=User" `
        -F "role=STUDENT" `
        -F "studentId=STU$timestamp" `
        -F "image=@$imagePath"
    
    Write-Host "Register Response: $registerOutput" -ForegroundColor Gray
    
    $registerJson = $registerOutput | ConvertFrom-Json
    if ($registerJson.userId) {
        Write-Host "✓ Registration successful! User ID: $($registerJson.userId)" -ForegroundColor Green
        $userId = $registerJson.userId
    } else {
        Write-Host "✗ Registration failed!" -ForegroundColor Red
        exit
    }
    
    Write-Host ""
    Write-Host "Step 2: Logging in to get JWT token..." -ForegroundColor Yellow
    
    $loginOutput = curl.exe -s -X POST "$baseUrl/api/auth/login" `
        -F "userName=$userName" `
        -F "password=$password" `
        -F "image=@$imagePath"
    
    Write-Host "Login Response (truncated): $($loginOutput.Substring(0, [Math]::Min(200, $loginOutput.Length)))..." -ForegroundColor Gray
    
    $loginJson = $loginOutput | ConvertFrom-Json
    if ($loginJson.token) {
        Write-Host "✓ Login successful! Got JWT token" -ForegroundColor Green
        $token = $loginJson.token
        Write-Host "  Token (first 50 chars): $($token.Substring(0, 50))..." -ForegroundColor Gray
    } else {
        Write-Host "✗ Login failed!" -ForegroundColor Red
        exit
    }
    
    Write-Host ""
    Write-Host "Step 3: Accessing enrollment endpoint WITH token..." -ForegroundColor Yellow
    
    $enrollOutput = curl.exe -s -X POST "$baseUrl/api/enrollment/enroll" `
        -H "Authorization: Bearer $token" `
        -F "studentId=$userId" `
        -F "image=@$imagePath"
    
    Write-Host "Enrollment Response: $enrollOutput" -ForegroundColor Gray
    
    if ($enrollOutput -match "success" -or $enrollOutput -match "Enrolled" -or $enrollOutput -notmatch "Unauthorized") {
        Write-Host "✓ SUCCESS! Enrollment endpoint accessed with authentication!" -ForegroundColor Green
    } else {
        Write-Host "✗ Still getting error" -ForegroundColor Red
    }
    
    Write-Host ""
    Write-Host "=== Your JWT Token ===" -ForegroundColor Cyan
    Write-Host $token -ForegroundColor White
    Write-Host ""
    Write-Host "Save this token to test manually!" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "=== Common Issues ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "If you're STILL getting 'Unauthorized':" -ForegroundColor Yellow
Write-Host "  1. Make sure you're sending: Authorization: Bearer <token>" -ForegroundColor White
Write-Host "     - Note the space after 'Bearer'" -ForegroundColor Gray
Write-Host "     - Token should be the full JWT string" -ForegroundColor Gray
Write-Host ""
Write-Host "  2. Check your request tool (Postman/curl/etc):" -ForegroundColor White
Write-Host "     - Header name: Authorization" -ForegroundColor Gray
Write-Host "     - Header value: Bearer eyJhbGci..." -ForegroundColor Gray
Write-Host ""
Write-Host "  3. Token might be expired (24 hours)" -ForegroundColor White
Write-Host "     - Login again to get a fresh token" -ForegroundColor Gray
Write-Host ""
Write-Host "  4. Check backend logs for errors" -ForegroundColor White
Write-Host "     - Look in the terminal where Spring Boot is running" -ForegroundColor Gray
