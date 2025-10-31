# Quick Backend Restart Script
# This script stops the current Java processes and restarts Spring Boot

Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Spring Boot Backend Restart Script" -ForegroundColor Cyan
Write-Host "================================================" -ForegroundColor Cyan
Write-Host ""

# Step 1: Stop existing Java processes
Write-Host "Step 1: Stopping existing Java processes..." -ForegroundColor Yellow
$javaProcesses = Get-Process -Name "java" -ErrorAction SilentlyContinue

if ($javaProcesses) {
    Write-Host "Found $($javaProcesses.Count) Java process(es). Stopping..." -ForegroundColor Yellow
    Stop-Process -Name "java" -Force -ErrorAction SilentlyContinue
    Start-Sleep -Seconds 2
    Write-Host "✓ Java processes stopped" -ForegroundColor Green
} else {
    Write-Host "No Java processes found running" -ForegroundColor Gray
}

# Step 2: Navigate to backend directory
Write-Host ""
Write-Host "Step 2: Navigating to backend directory..." -ForegroundColor Yellow
Set-Location -Path "d:\PROJECT\Exam-Anti-Cheating\anti-cheating-backend"
Write-Host "✓ In backend directory" -ForegroundColor Green

# Step 3: Start Spring Boot
Write-Host ""
Write-Host "Step 3: Starting Spring Boot application..." -ForegroundColor Yellow
Write-Host "This will take about 30-60 seconds..." -ForegroundColor Gray
Write-Host ""
Write-Host "Note: The question_option table will be recreated with column 'option_text'" -ForegroundColor Cyan
Write-Host ""

# Start Spring Boot
.\mvnw spring-boot:run

Write-Host ""
Write-Host "================================================" -ForegroundColor Cyan
Write-Host "Backend restart complete!" -ForegroundColor Green
Write-Host "================================================" -ForegroundColor Cyan
