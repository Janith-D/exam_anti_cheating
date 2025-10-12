// Content script for Anti-Cheating Extension
// Monitors user activities on exam pages

console.log('Anti-Cheating Extension: Content script loaded');

let isMonitoring = false;
let examSessionId = null;

// Listen for messages from background script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "startMonitoring") {
    isMonitoring = true;
    console.log('Monitoring started');
    sendResponse({ success: true });
  }
  
  if (request.action === "stopMonitoring") {
    isMonitoring = false;
    console.log('Monitoring stopped');
    sendResponse({ success: true });
  }
  
  if (request.action === "setExamSession") {
    examSessionId = request.examSessionId;
    sendResponse({ success: true });
  }
});

// Helper function to log events
function logEvent(type, details) {
  if (!isMonitoring) return;
  
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      type: type,
      details: details,
      examSessionId: examSessionId,
      timestamp: new Date().toISOString()
    }
  }, (response) => {
    if (response && response.success) {
      console.log(`Event logged: ${type}`);
    } else {
      console.error(`Failed to log event: ${type}`);
    }
  });
}

// Monitor window blur (leaving the exam page)
window.addEventListener('blur', () => {
  if (isMonitoring) {
    logEvent('WINDOW_BLUR', 'User focus left the exam window');
  }
});

// Monitor window focus (returning to exam page)
window.addEventListener('focus', () => {
  if (isMonitoring) {
    logEvent('WINDOW_FOCUS', 'User returned focus to exam window');
  }
});

// Monitor copy operations
document.addEventListener('copy', (e) => {
  if (isMonitoring) {
    const selectedText = window.getSelection().toString().substring(0, 100); // First 100 chars
    logEvent('COPY', `Copied text: "${selectedText}..."`);
  }
});

// Monitor paste operations
document.addEventListener('paste', (e) => {
  if (isMonitoring) {
    logEvent('PASTE', 'Pasted text into exam page');
  }
});

// Monitor right-click (context menu)
document.addEventListener('contextmenu', (e) => {
  if (isMonitoring) {
    logEvent('RIGHT_CLICK', `Right-clicked on: ${e.target.tagName}`);
    
    // Optionally prevent right-click during exam
    // e.preventDefault();
    // return false;
  }
});

// Monitor keyboard shortcuts
document.addEventListener('keydown', (e) => {
  if (!isMonitoring) return;
  
  // Detect suspicious key combinations
  if (e.ctrlKey || e.metaKey) {
    let combo = [];
    if (e.ctrlKey) combo.push('Ctrl');
    if (e.metaKey) combo.push('Cmd');
    if (e.shiftKey) combo.push('Shift');
    if (e.altKey) combo.push('Alt');
    combo.push(e.key.toUpperCase());
    
    const comboStr = combo.join('+');
    
    // Log suspicious combinations
    const suspiciousCombos = ['Ctrl+C', 'Ctrl+V', 'Ctrl+X', 'Ctrl+A', 'Ctrl+F', 'Ctrl+H', 'Ctrl+T', 'Ctrl+N', 'Ctrl+W'];
    if (suspiciousCombos.some(sc => comboStr.includes(sc.replace('Ctrl', 'Ctrl')))) {
      logEvent('KEY_COMBINATION', `Pressed: ${comboStr}`);
    }
  }
  
  // Detect F12 (DevTools)
  if (e.key === 'F12') {
    logEvent('BROWSER_DEVTOOLS', 'Attempted to open browser developer tools');
    // Optionally prevent DevTools
    // e.preventDefault();
    // return false;
  }
  
  // Detect Escape (potential fullscreen exit)
  if (e.key === 'Escape') {
    logEvent('KEY_COMBINATION', 'Pressed Escape key (potential fullscreen exit)');
  }
});

// Monitor fullscreen changes
document.addEventListener('fullscreenchange', () => {
  if (isMonitoring) {
    if (!document.fullscreenElement) {
      logEvent('FULLSCREEN_EXIT', 'User exited fullscreen mode');
    }
  }
});

// Monitor visibility changes (tab switching)
document.addEventListener('visibilitychange', () => {
  if (isMonitoring) {
    if (document.hidden) {
      logEvent('TAB_SWITCH', 'User switched away from exam tab');
    } else {
      logEvent('TAB_SWITCH', 'User returned to exam tab');
    }
  }
});

// Monitor mouse leaving window
document.addEventListener('mouseleave', (e) => {
  if (isMonitoring && e.clientY <= 0) {
    logEvent('SUSPICIOUS_ACTIVITY', 'Mouse left window boundaries (top)');
  }
});

// Detect multiple monitors (experimental)
function detectMultipleMonitors() {
  if (window.screen.isExtended !== undefined) {
    return window.screen.isExtended;
  }
  // Fallback: check if availWidth/availHeight differ significantly from width/height
  return Math.abs(screen.availWidth - screen.width) > 100 || 
         Math.abs(screen.availHeight - screen.height) > 100;
}

// Check for multiple monitors on load
if (isMonitoring && detectMultipleMonitors()) {
  logEvent('MULTIPLE_MONITORS', 'Multiple monitors detected');
}

// Periodic activity check (every 30 seconds)
setInterval(() => {
  if (isMonitoring) {
    // This could be enhanced to capture webcam snapshot
    logEvent('SNAPSHOT', 'Periodic activity check');
  }
}, 30000);

// Detect page unload (closing exam)
window.addEventListener('beforeunload', () => {
  if (isMonitoring) {
    logEvent('SUSPICIOUS_ACTIVITY', 'User attempted to close/refresh exam page');
  }
});

// Initialize monitoring if extension is already active
chrome.runtime.sendMessage({ action: "getStatus" }, (response) => {
  if (response && response.status === 'Monitoring Active') {
    isMonitoring = true;
    console.log('Monitoring auto-started from extension state');
  }
});