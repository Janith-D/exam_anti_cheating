# Quick script to get a fresh JWT token

Write-Host "🔐 Getting fresh JWT token..." -ForegroundColor Cyan
Write-Host ""

$loginUrl = "http://localhost:8080/api/auth/login"
$body = @{
    username = "teacher"
    password = "teacher123"
} | ConvertTo-Json

try {
    $response = Invoke-RestMethod -Uri $loginUrl -Method Post -Body $body -ContentType "application/json"
    
    if ($response.token) {
        Write-Host "✅ SUCCESS! Here's your fresh token:" -ForegroundColor Green
        Write-Host ""
        Write-Host $response.token -ForegroundColor Yellow
        Write-Host ""
        Write-Host "📋 Copy this token and paste it in the extension popup!" -ForegroundColor Cyan
        Write-Host ""
        
        # Copy to clipboard if available
        try {
            Set-Clipboard -Value $response.token
            Write-Host "✨ Token copied to clipboard!" -ForegroundColor Green
        } catch {
            Write-Host "⚠️ Could not copy to clipboard, please copy manually" -ForegroundColor Yellow
        }
    } else {
        Write-Host "❌ No token in response" -ForegroundColor Red
        Write-Host $response
    }
} catch {
    Write-Host "❌ ERROR: Could not get token" -ForegroundColor Red
    Write-Host "Make sure the backend is running on http://localhost:8080" -ForegroundColor Yellow
    Write-Host ""
    Write-Host "Error details:" -ForegroundColor Red
    Write-Host $_.Exception.Message
}
