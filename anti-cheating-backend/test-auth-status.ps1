# Test Authentication Status
Write-Host "🔍 Testing Authentication Status..." -ForegroundColor Cyan

# Check if running
$process = Get-Process -Name java -ErrorAction SilentlyContinue
if ($process) {
    Write-Host "✅ Backend is running (PID: $($process.Id))" -ForegroundColor Green
} else {
    Write-Host "❌ Backend is NOT running!" -ForegroundColor Red
    exit 1
}

# Test endpoints
$baseUrl = "http://localhost:8080"

Write-Host "`n📋 Testing endpoints..." -ForegroundColor Yellow

# 1. Test health endpoint (no auth)
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/api/health" -Method Get
    Write-Host "✅ Health endpoint: OK" -ForegroundColor Green
} catch {
    Write-Host "❌ Health endpoint failed: $($_.Exception.Message)" -ForegroundColor Red
}

# 2. Test sessions endpoint (requires auth)
Write-Host "`n🔐 Testing /api/sessions endpoint (requires ADMIN auth)..." -ForegroundColor Yellow
try {
    $sessions = Invoke-RestMethod -Uri "$baseUrl/api/sessions" -Method Get
    Write-Host "✅ Sessions endpoint: OK (returned $($sessions.Count) sessions)" -ForegroundColor Green
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 401) {
        Write-Host "❌ 401 Unauthorized - You need to login!" -ForegroundColor Red
        Write-Host "   Token is either:" -ForegroundColor Yellow
        Write-Host "   - Missing (not logged in)" -ForegroundColor Yellow
        Write-Host "   - Expired (login again)" -ForegroundColor Yellow
        Write-Host "   - Invalid role (need ADMIN)" -ForegroundColor Yellow
    } elseif ($statusCode -eq 403) {
        Write-Host "❌ 403 Forbidden - You don't have ADMIN role!" -ForegroundColor Red
    } else {
        Write-Host "❌ Error $statusCode : $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`n💡 Solution:" -ForegroundColor Cyan
Write-Host "1. Open the Angular app in browser" -ForegroundColor White
Write-Host "2. Login with an ADMIN account" -ForegroundColor White
Write-Host "3. The JWT token will be stored in localStorage" -ForegroundColor White
Write-Host "4. Navigate to Exam Sessions page" -ForegroundColor White
Write-Host "`nOR create a test admin account first." -ForegroundColor White
