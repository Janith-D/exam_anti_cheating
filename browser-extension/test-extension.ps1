# Browser Extension Testing Script
# Run this after loading the extension in Chrome

Write-Host "================================" -ForegroundColor Cyan
Write-Host "Browser Extension Test Script" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host ""

# Check if backend is running
Write-Host "1. Checking backend status..." -ForegroundColor Yellow
try {
    $response = Invoke-WebRequest -Uri "http://localhost:8080/health" -Method GET -ErrorAction Stop
    Write-Host "   ✅ Backend is running!" -ForegroundColor Green
} catch {
    Write-Host "   ❌ Backend is NOT running!" -ForegroundColor Red
    Write-Host "   Start it with: cd anti-cheating-backend; .\mvnw.cmd spring-boot:run" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "2. Testing event logging endpoint..." -ForegroundColor Yellow

# Prompt for JWT token
Write-Host ""
Write-Host "Do you have a JWT token? (y/n): " -ForegroundColor Cyan -NoNewline
$hasToken = Read-Host

if ($hasToken -eq "y") {
    Write-Host "Enter your JWT token: " -ForegroundColor Cyan -NoNewline
    $token = Read-Host
    
    Write-Host "Enter student ID (e.g., 1): " -ForegroundColor Cyan -NoNewline
    $studentId = Read-Host
    
    # Test event logging
    Write-Host ""
    Write-Host "3. Sending test event..." -ForegroundColor Yellow
    
    try {
        $response = curl.exe -X POST "http://localhost:8080/api/events/log" `
            -H "Authorization: Bearer $token" `
            -F "studentId=$studentId" `
            -F "type=TAB_SWITCH" `
            -F "details=Test event from extension test script" `
            --silent
        
        Write-Host "   ✅ Event logged successfully!" -ForegroundColor Green
        Write-Host "   Response: $response" -ForegroundColor Gray
    } catch {
        Write-Host "   ❌ Failed to log event!" -ForegroundColor Red
        Write-Host "   Error: $_" -ForegroundColor Red
    }
    
} else {
    Write-Host ""
    Write-Host "To get a JWT token:" -ForegroundColor Yellow
    Write-Host "1. Register a student user:" -ForegroundColor White
    Write-Host '   curl.exe -X POST http://localhost:8080/api/auth/register \' -ForegroundColor Gray
    Write-Host '     -F "userName=student1" \' -ForegroundColor Gray
    Write-Host '     -F "password=Pass123!" \' -ForegroundColor Gray
    Write-Host '     -F "email=student1@test.com" \' -ForegroundColor Gray
    Write-Host '     -F "firstName=John" \' -ForegroundColor Gray
    Write-Host '     -F "lastName=Doe" \' -ForegroundColor Gray
    Write-Host '     -F "role=STUDENT" \' -ForegroundColor Gray
    Write-Host '     -F "studentId=STU001" \' -ForegroundColor Gray
    Write-Host '     -F "image=@C:\path\to\photo.jpg"' -ForegroundColor Gray
    Write-Host ""
    Write-Host "2. Login to get token:" -ForegroundColor White
    Write-Host '   curl.exe -X POST http://localhost:8080/api/auth/login \' -ForegroundColor Gray
    Write-Host '     -F "userName=student1" \' -ForegroundColor Gray
    Write-Host '     -F "password=Pass123!" \' -ForegroundColor Gray
    Write-Host '     -F "image=@C:\path\to\photo.jpg"' -ForegroundColor Gray
}

Write-Host ""
Write-Host "================================" -ForegroundColor Cyan
Write-Host "Extension Installation Steps:" -ForegroundColor Cyan
Write-Host "================================" -ForegroundColor Cyan
Write-Host "1. Open Chrome: chrome://extensions/" -ForegroundColor White
Write-Host "2. Enable 'Developer mode' (top-right)" -ForegroundColor White
Write-Host "3. Click 'Load unpacked'" -ForegroundColor White
Write-Host "4. Select folder: d:\PROJECT\Exam-Anti-Cheating\browser-extension" -ForegroundColor White
Write-Host "5. Click extension icon in toolbar" -ForegroundColor White
Write-Host "6. Enter Student ID and JWT Token" -ForegroundColor White
Write-Host "7. Click 'Save Credentials'" -ForegroundColor White
Write-Host "8. Click 'Start Monitoring'" -ForegroundColor White
Write-Host ""
Write-Host "✅ Extension will now log all your activities!" -ForegroundColor Green
Write-Host ""
Write-Host "Test by:" -ForegroundColor Yellow
Write-Host "  - Switching tabs" -ForegroundColor White
Write-Host "  - Copying text (Ctrl+C)" -ForegroundColor White
Write-Host "  - Pasting text (Ctrl+V)" -ForegroundColor White
Write-Host "  - Right-clicking" -ForegroundColor White
Write-Host "  - Pressing F12" -ForegroundColor White
Write-Host ""
Write-Host "Check events logged:" -ForegroundColor Yellow
Write-Host "  GET http://localhost:8080/api/events/student/{studentId}/all" -ForegroundColor Gray
Write-Host ""
