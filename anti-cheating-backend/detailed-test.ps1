# Detailed Request Test
# This will show you EXACTLY what's happening with your request

Write-Host "=== Detailed Enrollment Endpoint Test ===" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8080"

Write-Host "How do you want to test?" -ForegroundColor Yellow
Write-Host "  1. I already have a JWT token (manual test)"
Write-Host "  2. Run full flow (register + login + test)"
Write-Host ""
$choice = Read-Host "Enter choice (1 or 2)"

if ($choice -eq "1") {
    Write-Host ""
    Write-Host "=== Manual Test with Existing Token ===" -ForegroundColor Cyan
    Write-Host ""
    
    $token = Read-Host "Paste your JWT token"
    $userId = Read-Host "Enter your user ID (studentId)"
    $imagePath = Read-Host "Enter image path"
    
    if (-not (Test-Path $imagePath)) {
        Write-Host "✗ Image not found!" -ForegroundColor Red
        exit
    }
    
    Write-Host ""
    Write-Host "Testing enrollment endpoint..." -ForegroundColor Yellow
    Write-Host "  URL: $baseUrl/api/enrollment/enroll" -ForegroundColor Gray
    Write-Host "  Method: POST" -ForegroundColor Gray
    Write-Host "  Header: Authorization: Bearer $($token.Substring(0, 20))..." -ForegroundColor Gray
    Write-Host "  Body: studentId=$userId, image=<file>" -ForegroundColor Gray
    Write-Host ""
    
    # Method 1: Using curl.exe (more reliable for multipart)
    Write-Host "Attempt 1: Using curl.exe..." -ForegroundColor Yellow
    $output1 = curl.exe -v -X POST "$baseUrl/api/enrollment/enroll" `
        -H "Authorization: Bearer $token" `
        -F "studentId=$userId" `
        -F "image=@$imagePath" 2>&1
    
    Write-Host ""
    Write-Host "Response:" -ForegroundColor White
    Write-Host $output1 -ForegroundColor Gray
    Write-Host ""
    
    # Check for common issues
    if ($output1 -match "401") {
        Write-Host "❌ ERROR: 401 Unauthorized" -ForegroundColor Red
        Write-Host ""
        Write-Host "Possible causes:" -ForegroundColor Yellow
        Write-Host "  1. Token format is wrong - should be: 'Bearer <token>'" -ForegroundColor White
        Write-Host "  2. Token is expired (24 hour lifetime)" -ForegroundColor White
        Write-Host "  3. Token is invalid or corrupted" -ForegroundColor White
        Write-Host "  4. User doesn't have STUDENT role" -ForegroundColor White
        Write-Host ""
        
        # Try to decode the token
        Write-Host "Debugging token..." -ForegroundColor Yellow
        $tokenParts = $token.Split(".")
        if ($tokenParts.Count -eq 3) {
            Write-Host "✓ Token has 3 parts (header.payload.signature)" -ForegroundColor Green
            
            # Decode payload (base64)
            try {
                $payload = $tokenParts[1]
                # Add padding if needed
                while ($payload.Length % 4 -ne 0) { $payload += "=" }
                $payloadBytes = [Convert]::FromBase64String($payload)
                $payloadJson = [System.Text.Encoding]::UTF8.GetString($payloadBytes)
                Write-Host "Token payload:" -ForegroundColor White
                Write-Host $payloadJson -ForegroundColor Gray
                
                $payloadObj = $payloadJson | ConvertFrom-Json
                if ($payloadObj.exp) {
                    $expDate = [DateTimeOffset]::FromUnixTimeSeconds($payloadObj.exp).LocalDateTime
                    Write-Host "Token expires: $expDate" -ForegroundColor White
                    if ($expDate -lt (Get-Date)) {
                        Write-Host "❌ TOKEN IS EXPIRED!" -ForegroundColor Red
                    } else {
                        Write-Host "✓ Token is not expired yet" -ForegroundColor Green
                    }
                }
            } catch {
                Write-Host "! Could not decode token payload" -ForegroundColor Yellow
            }
        } else {
            Write-Host "❌ Token format is INVALID (should have 3 parts separated by dots)" -ForegroundColor Red
        }
        
    } elseif ($output1 -match "403") {
        Write-Host "❌ ERROR: 403 Forbidden" -ForegroundColor Red
        Write-Host "  Token is valid but user doesn't have required role (STUDENT)" -ForegroundColor Yellow
    } elseif ($output1 -match "200") {
        Write-Host "✅ SUCCESS! Request accepted!" -ForegroundColor Green
    } else {
        Write-Host "⚠ Check response above for details" -ForegroundColor Yellow
    }
    
} elseif ($choice -eq "2") {
    $imagePath = Read-Host "Enter image path for testing"
    
    if (-not (Test-Path $imagePath)) {
        Write-Host "✗ Image not found!" -ForegroundColor Red
        exit
    }
    
    Write-Host ""
    Write-Host "=== Running Full Flow ===" -ForegroundColor Cyan
    
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $userName = "test$timestamp"
    $password = "Pass123!"
    $email = "$userName@test.com"
    
    # Step 1: Register
    Write-Host ""
    Write-Host "Step 1: Registering user '$userName'..." -ForegroundColor Yellow
    $regOutput = curl.exe -s -X POST "$baseUrl/api/auth/register" `
        -F "userName=$userName" `
        -F "password=$password" `
        -F "email=$email" `
        -F "firstName=Test" `
        -F "lastName=User" `
        -F "role=STUDENT" `
        -F "studentId=S$timestamp" `
        -F "image=@$imagePath"
    
    Write-Host "Response: $regOutput" -ForegroundColor Gray
    
    try {
        $regJson = $regOutput | ConvertFrom-Json
        if ($regJson.userId) {
            Write-Host "✓ Registered! User ID: $($regJson.userId)" -ForegroundColor Green
            $userId = $regJson.userId
        } else {
            Write-Host "✗ Registration failed: $regOutput" -ForegroundColor Red
            exit
        }
    } catch {
        Write-Host "✗ Registration failed - invalid response" -ForegroundColor Red
        exit
    }
    
    # Step 2: Login
    Write-Host ""
    Write-Host "Step 2: Logging in..." -ForegroundColor Yellow
    $loginOutput = curl.exe -s -X POST "$baseUrl/api/auth/login" `
        -F "userName=$userName" `
        -F "password=$password" `
        -F "image=@$imagePath"
    
    try {
        $loginJson = $loginOutput | ConvertFrom-Json
        if ($loginJson.token) {
            Write-Host "✓ Login successful!" -ForegroundColor Green
            Write-Host "  Token: $($loginJson.token.Substring(0, 50))..." -ForegroundColor Gray
            Write-Host "  Role: $($loginJson.role)" -ForegroundColor Gray
            $token = $loginJson.token
        } else {
            Write-Host "✗ Login failed: $loginOutput" -ForegroundColor Red
            exit
        }
    } catch {
        Write-Host "✗ Login failed - invalid response" -ForegroundColor Red
        Write-Host "Response: $loginOutput" -ForegroundColor Gray
        exit
    }
    
    # Step 3: Test enrollment
    Write-Host ""
    Write-Host "Step 3: Testing enrollment endpoint WITH token..." -ForegroundColor Yellow
    Write-Host "  Sending: POST $baseUrl/api/enrollment/enroll" -ForegroundColor Gray
    Write-Host "  Header: Authorization: Bearer <token>" -ForegroundColor Gray
    Write-Host "  Body: studentId=$userId, image=<file>" -ForegroundColor Gray
    Write-Host ""
    
    $enrollOutput = curl.exe -v -X POST "$baseUrl/api/enrollment/enroll" `
        -H "Authorization: Bearer $token" `
        -F "studentId=$userId" `
        -F "image=@$imagePath" 2>&1
    
    Write-Host ""
    Write-Host "Full Response:" -ForegroundColor White
    Write-Host $enrollOutput -ForegroundColor Gray
    Write-Host ""
    
    if ($enrollOutput -match "200 OK" -or $enrollOutput -match "201") {
        Write-Host "✅ SUCCESS! Enrollment endpoint accessible!" -ForegroundColor Green
    } elseif ($enrollOutput -match "401") {
        Write-Host "❌ Still getting 401 Unauthorized!" -ForegroundColor Red
        Write-Host ""
        Write-Host "This is a configuration issue. Check:" -ForegroundColor Yellow
        Write-Host "  1. Backend logs for JWT errors" -ForegroundColor White
        Write-Host "  2. Make sure backend was restarted after changes" -ForegroundColor White
        Write-Host "  3. Check if JWT secret is configured properly" -ForegroundColor White
    } else {
        Write-Host "⚠ Check response above" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "Your credentials for manual testing:" -ForegroundColor Cyan
    Write-Host "  Username: $userName" -ForegroundColor White
    Write-Host "  Password: $password" -ForegroundColor White
    Write-Host "  User ID: $userId" -ForegroundColor White
    Write-Host "  Token: $token" -ForegroundColor White
}

Write-Host ""
Write-Host "=== Debug Complete ===" -ForegroundColor Cyan
