Write-Host "=== EXAM SESSION ID DIAGNOSTIC ===" -ForegroundColor Cyan
Write-Host ""

Write-Host "1. Checking if backend is running..." -ForegroundColor Yellow
try {
    $health = Invoke-RestMethod -Uri "http://localhost:8080/actuator/health" -ErrorAction Stop
    Write-Host "   Backend Status: $($health.status)" -ForegroundColor Green
} catch {
    Write-Host "   Backend is NOT running!" -ForegroundColor Red
    Write-Host "   Start it first: mvn spring-boot:run" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "2. SQL Queries to Run in MySQL:" -ForegroundColor Yellow
Write-Host ""
Write-Host "   -- Check exam sessions" -ForegroundColor Cyan
Write-Host "   SELECT * FROM exam_sessions;" -ForegroundColor White
Write-Host ""
Write-Host "   -- Check recent events with exam_session_id" -ForegroundColor Cyan
Write-Host "   SELECT id, student_id, exam_session_id, type, details, timestamp" -ForegroundColor White
Write-Host "   FROM events" -ForegroundColor White
Write-Host "   ORDER BY timestamp DESC" -ForegroundColor White
Write-Host "   LIMIT 10;" -ForegroundColor White
Write-Host ""
Write-Host "   -- Count events WITH exam_session_id" -ForegroundColor Cyan
Write-Host "   SELECT COUNT(*) as with_session FROM events WHERE exam_session_id IS NOT NULL;" -ForegroundColor White
Write-Host ""
Write-Host "   -- Count events WITHOUT exam_session_id" -ForegroundColor Cyan
Write-Host "   SELECT COUNT(*) as without_session FROM events WHERE exam_session_id IS NULL;" -ForegroundColor White
Write-Host ""

Write-Host "3. Extension Console Commands:" -ForegroundColor Yellow
Write-Host "   Open Chrome Console (F12) and run:" -ForegroundColor Cyan
Write-Host ""
Write-Host "   // Check if examSessionId is in storage" -ForegroundColor Gray
Write-Host "   chrome.storage.local.get(['examSessionId', 'isMonitoring'], console.log)" -ForegroundColor White
Write-Host ""
Write-Host "   Expected output:" -ForegroundColor Gray
Write-Host "   { examSessionId: '1', isMonitoring: true }" -ForegroundColor Green
Write-Host ""

Write-Host "4. Actions to Take:" -ForegroundColor Yellow
Write-Host "   a) Run the SQL queries above in MySQL Workbench" -ForegroundColor White
Write-Host "   b) Run the Chrome console command" -ForegroundColor White
Write-Host "   c) Report back the results" -ForegroundColor White
Write-Host ""

Write-Host "==================================" -ForegroundColor Cyan
