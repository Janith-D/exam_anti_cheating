# Quick Login Script - Get Fresh JWT Token
# Run this to get a new token after backend restart

Write-Host "==================================" -ForegroundColor Cyan
Write-Host "  GET FRESH JWT TOKEN" -ForegroundColor Cyan
Write-Host "==================================" -ForegroundColor Cyan
Write-Host ""

# Configuration - CHANGE THESE VALUES
$USERNAME = "testStudent"
$PASSWORD = "Test123!"
$IMAGE_PATH = "D:\photos\test.jpg"  # ← Change this to your photo path

# If the default image path does not exist, prompt the user for a valid path
if (-not (Test-Path $IMAGE_PATH)) {
    Write-Host "Default image path not found: $IMAGE_PATH" -ForegroundColor Yellow
    $IMAGE_PATH = Read-Host "Please enter the full path to your photo"
}
$BACKEND_URL = "http://localhost:8080"

# Check if image exists
if (-not (Test-Path $IMAGE_PATH)) {
    Write-Host "ERROR: Image not found at: $IMAGE_PATH" -ForegroundColor Red
    Write-Host "Please edit this script and set the correct IMAGE_PATH" -ForegroundColor Yellow
    exit
}

Write-Host "Configuration:" -ForegroundColor Green
Write-Host "  Backend URL: $BACKEND_URL"
Write-Host "  Username: $USERNAME"
Write-Host "  Image: $IMAGE_PATH"
Write-Host ""

# Step 1: Check backend health
Write-Host "[1/3] Checking backend health..." -ForegroundColor Yellow
try {
    $healthResponse = Invoke-RestMethod -Uri "$BACKEND_URL/health" -Method Get -ErrorAction Stop
    Write-Host "  ✅ Backend is UP" -ForegroundColor Green
} catch {
    Write-Host "  ❌ Backend is not running!" -ForegroundColor Red
    Write-Host "  Please start the backend first:" -ForegroundColor Yellow
    Write-Host "    cd anti-cheating-backend" -ForegroundColor Cyan
    Write-Host "    .\mvnw.cmd spring-boot:run" -ForegroundColor Cyan
    exit
}

Write-Host ""

# Step 2: Login
Write-Host "[2/3] Logging in..." -ForegroundColor Yellow

# Prepare multipart form data
$form = @{
    userName = $USERNAME
    password = $PASSWORD
    image = Get-Item -Path $IMAGE_PATH
}

try {
    $loginResponse = Invoke-RestMethod -Uri "$BACKEND_URL/api/auth/login" -Method Post -Form $form -ErrorAction Stop
    
    Write-Host "  ✅ Login successful!" -ForegroundColor Green
    Write-Host ""
    
    # Step 3: Display token
    Write-Host "[3/3] Your Fresh Token:" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "=================================" -ForegroundColor Cyan
    Write-Host "STUDENT ID: $($loginResponse.studentId)" -ForegroundColor White -BackgroundColor DarkBlue
    Write-Host "=================================" -ForegroundColor Cyan
    Write-Host ""
    Write-Host "=================================" -ForegroundColor Cyan
    Write-Host "JWT TOKEN (copy this):" -ForegroundColor White
    Write-Host $loginResponse.token -ForegroundColor Green
    Write-Host "=================================" -ForegroundColor Cyan
    Write-Host ""
    
    Write-Host "Additional Info:" -ForegroundColor Yellow
    Write-Host "  Username: $($loginResponse.userName)"
    Write-Host "  Email: $($loginResponse.email)"
    Write-Host "  Role: $($loginResponse.role)"
    Write-Host "  Student ID: $($loginResponse.studentId)"
    Write-Host ""
    
    # Save to clipboard if possible
    try {
        Set-Clipboard -Value $loginResponse.token
        Write-Host "✅ Token copied to clipboard!" -ForegroundColor Green
    } catch {
        Write-Host "⚠️  Could not copy to clipboard, please copy manually" -ForegroundColor Yellow
    }
    
    Write-Host ""
    Write-Host "==================================" -ForegroundColor Cyan
    Write-Host "NEXT STEPS:" -ForegroundColor Cyan
    Write-Host "==================================" -ForegroundColor Cyan
    Write-Host "1. Go to chrome://extensions/" -ForegroundColor White
    Write-Host "2. Reload the Anti-Cheating extension (🔄)" -ForegroundColor White
    Write-Host "3. Click extension icon" -ForegroundColor White
    Write-Host "4. Paste the JWT Token (Ctrl+V)" -ForegroundColor White
    Write-Host "5. Enter Student ID: $($loginResponse.studentId)" -ForegroundColor White
    Write-Host "6. Click 'Save Credentials'" -ForegroundColor White
    Write-Host "7. Click 'Start Monitoring'" -ForegroundColor White
    Write-Host "8. Test with Ctrl+C" -ForegroundColor White
    Write-Host ""
    
} catch {
    Write-Host "  ❌ Login failed!" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host ""
    Write-Host "  Possible reasons:" -ForegroundColor Yellow
    Write-Host "    - Wrong username/password" -ForegroundColor White
    Write-Host "    - User doesn't exist (register first)" -ForegroundColor White
    Write-Host "    - Image file not found or invalid" -ForegroundColor White
    Write-Host ""
    Write-Host "  To register a new user:" -ForegroundColor Yellow
    Write-Host "    curl.exe -X POST http://localhost:8080/api/auth/register \" -ForegroundColor Cyan
    Write-Host "      -F 'userName=$USERNAME' \" -ForegroundColor Cyan
    Write-Host "      -F 'password=$PASSWORD' \" -ForegroundColor Cyan
    Write-Host "      -F 'email=test@student.com' \" -ForegroundColor Cyan
    Write-Host "      -F 'firstName=Test' \" -ForegroundColor Cyan
    Write-Host "      -F 'lastName=Student' \" -ForegroundColor Cyan
    Write-Host "      -F 'role=STUDENT' \" -ForegroundColor Cyan
    Write-Host "      -F 'studentId=TEST001' \" -ForegroundColor Cyan
    Write-Host "      -F 'image=@$IMAGE_PATH'" -ForegroundColor Cyan
    Write-Host ""
}
