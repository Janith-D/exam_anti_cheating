# Anti-Cheating Backend API Test Script
# This script helps you test the authentication flow

Write-Host "=== Anti-Cheating Backend API Test ===" -ForegroundColor Cyan
Write-Host ""

# Configuration
$baseUrl = "http://localhost:8080"
$imagePath = Read-Host "Enter path to test image (e.g., C:\Users\YourName\Pictures\photo.jpg)"

if (-not (Test-Path $imagePath)) {
    Write-Host "Error: Image file not found at: $imagePath" -ForegroundColor Red
    exit
}

Write-Host ""
Write-Host "Step 1: Testing health endpoint..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/health" -Method Get
    Write-Host "✓ Backend is running!" -ForegroundColor Green
    Write-Host "  Status: $($health.status)"
} catch {
    Write-Host "✗ Backend is not running or not accessible" -ForegroundColor Red
    Write-Host "  Make sure the Spring Boot application is running on port 8080"
    exit
}

Write-Host ""
Write-Host "Step 2: Registering a test user..." -ForegroundColor Yellow
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$userName = "testuser_$timestamp"
$password = "TestPassword123!"
$email = "test_$timestamp@example.com"

try {
    # Create form data for registration
    $boundary = [System.Guid]::NewGuid().ToString()
    $LF = "`r`n"
    
    # Read image file
    $imageBytes = [System.IO.File]::ReadAllBytes($imagePath)
    $imageFileName = Split-Path $imagePath -Leaf
    
    # Build multipart form data
    $bodyLines = @(
        "--$boundary",
        "Content-Disposition: form-data; name=`"userName`"$LF",
        $userName,
        "--$boundary",
        "Content-Disposition: form-data; name=`"password`"$LF",
        $password,
        "--$boundary",
        "Content-Disposition: form-data; name=`"email`"$LF",
        $email,
        "--$boundary",
        "Content-Disposition: form-data; name=`"firstName`"$LF",
        "Test",
        "--$boundary",
        "Content-Disposition: form-data; name=`"lastName`"$LF",
        "User",
        "--$boundary",
        "Content-Disposition: form-data; name=`"role`"$LF",
        "STUDENT",
        "--$boundary",
        "Content-Disposition: form-data; name=`"studentId`"$LF",
        "STU$timestamp",
        "--$boundary",
        "Content-Disposition: form-data; name=`"image`"; filename=`"$imageFileName`"",
        "Content-Type: image/jpeg$LF"
    ) -join $LF
    
    $bodyLines += [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($imageBytes)
    $bodyLines += "$LF--$boundary--$LF"
    
    $headers = @{
        "Content-Type" = "multipart/form-data; boundary=$boundary"
    }
    
    $registerResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" -Method Post -Body $bodyLines -Headers $headers
    Write-Host "✓ Registration successful!" -ForegroundColor Green
    Write-Host "  User ID: $($registerResponse.userId)"
    Write-Host "  Enrollment ID: $($registerResponse.enrollmentId)"
    $userId = $registerResponse.userId
} catch {
    Write-Host "✗ Registration failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) {
        Write-Host "  Details: $($_.ErrorDetails.Message)"
    }
    exit
}

Write-Host ""
Write-Host "Step 3: Logging in to get JWT token..." -ForegroundColor Yellow
try {
    # Build multipart form data for login
    $boundary = [System.Guid]::NewGuid().ToString()
    $imageBytes = [System.IO.File]::ReadAllBytes($imagePath)
    
    $bodyLines = @(
        "--$boundary",
        "Content-Disposition: form-data; name=`"userName`"$LF",
        $userName,
        "--$boundary",
        "Content-Disposition: form-data; name=`"password`"$LF",
        $password,
        "--$boundary",
        "Content-Disposition: form-data; name=`"image`"; filename=`"$imageFileName`"",
        "Content-Type: image/jpeg$LF"
    ) -join $LF
    
    $bodyLines += [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($imageBytes)
    $bodyLines += "$LF--$boundary--$LF"
    
    $headers = @{
        "Content-Type" = "multipart/form-data; boundary=$boundary"
    }
    
    $loginResponse = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $bodyLines -Headers $headers
    Write-Host "✓ Login successful!" -ForegroundColor Green
    Write-Host "  Username: $($loginResponse.userName)"
    Write-Host "  Role: $($loginResponse.role)"
    Write-Host "  Token: $($loginResponse.token.Substring(0, 50))..."
    $token = $loginResponse.token
} catch {
    Write-Host "✗ Login failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) {
        Write-Host "  Details: $($_.ErrorDetails.Message)"
    }
    exit
}

Write-Host ""
Write-Host "Step 4: Testing enrollment endpoint with authentication..." -ForegroundColor Yellow
try {
    # Build multipart form data for enrollment
    $boundary = [System.Guid]::NewGuid().ToString()
    $imageBytes = [System.IO.File]::ReadAllBytes($imagePath)
    
    $bodyLines = @(
        "--$boundary",
        "Content-Disposition: form-data; name=`"studentId`"$LF",
        $userId,
        "--$boundary",
        "Content-Disposition: form-data; name=`"image`"; filename=`"$imageFileName`"",
        "Content-Type: image/jpeg$LF"
    ) -join $LF
    
    $bodyLines += [System.Text.Encoding]::GetEncoding("iso-8859-1").GetString($imageBytes)
    $bodyLines += "$LF--$boundary--$LF"
    
    $headers = @{
        "Content-Type" = "multipart/form-data; boundary=$boundary"
        "Authorization" = "Bearer $token"
    }
    
    $enrollResponse = Invoke-RestMethod -Uri "$baseUrl/api/enrollment/enroll" -Method Post -Body $bodyLines -Headers $headers
    Write-Host "✓ Enrollment endpoint accessed successfully!" -ForegroundColor Green
    Write-Host "  Message: $($enrollResponse.message)"
    if ($enrollResponse.enrollmentId) {
        Write-Host "  Enrollment ID: $($enrollResponse.enrollmentId)"
    }
} catch {
    Write-Host "✗ Enrollment request failed" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)"
    if ($_.ErrorDetails.Message) {
        Write-Host "  Details: $($_.ErrorDetails.Message)"
    }
}

Write-Host ""
Write-Host "=== Test Complete ===" -ForegroundColor Cyan
Write-Host ""
Write-Host "Your JWT Token (save this for manual testing):" -ForegroundColor Yellow
Write-Host $token -ForegroundColor Gray
Write-Host ""
Write-Host "To use this token in Postman or other tools:" -ForegroundColor Yellow
Write-Host "  Add header: Authorization: Bearer $token" -ForegroundColor Gray
