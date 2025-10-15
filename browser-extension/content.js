// Content script for Anti-Cheating Extension
// Monitors user activities on exam pages

console.log('Anti-Cheating Extension: Content script loaded');

let isMonitoring = false;
let examSessionId = null;

// CRITICAL FIX: Load monitoring state from storage on startup
chrome.storage.local.get(['isMonitoring', 'examSessionId'], (result) => {
  if (result.isMonitoring) {
    isMonitoring = true;
    examSessionId = result.examSessionId;
    console.log('🔴 Restored monitoring state: ACTIVE');
    console.log('📋 Exam Session ID:', examSessionId);
    
    // Apply CSS protection if monitoring was active
    applySelectionBlockingCSS();
  } else {
    console.log('⚪ Monitoring state: INACTIVE');
  }
});

// Listen for messages from background script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "startMonitoring") {
    isMonitoring = true;
    console.log('✅ Monitoring started in content script');
    
    // Apply CSS to disable text selection as extra protection
    applySelectionBlockingCSS();
    
    sendResponse({ success: true });
  }
  
  if (request.action === "stopMonitoring") {
    isMonitoring = false;
    console.log('⛔ Monitoring stopped in content script');
    
    // Remove CSS protection
    removeSelectionBlockingCSS();
    
    sendResponse({ success: true });
  }
  
  if (request.action === "setExamSession") {
    examSessionId = request.examSessionId;
    console.log('📋 Exam session set to:', examSessionId);
    sendResponse({ success: true });
  }
});

// Apply CSS to disable text selection
function applySelectionBlockingCSS() {
  const style = document.createElement('style');
  style.id = 'anti-cheat-selection-block';
  style.textContent = `
    * {
      -webkit-user-select: none !important;
      -moz-user-select: none !important;
      -ms-user-select: none !important;
      user-select: none !important;
    }
    
    /* Also disable drag and drop */
    * {
      -webkit-user-drag: none !important;
      user-drag: none !important;
    }
  `;
  document.head.appendChild(style);
  console.log('Applied selection blocking CSS');
}

// Remove CSS protection
function removeSelectionBlockingCSS() {
  const style = document.getElementById('anti-cheat-selection-block');
  if (style) {
    style.remove();
    console.log('Removed selection blocking CSS');
  }
}

// Helper function to log events
function logEvent(type, details) {
  if (!isMonitoring) {
    console.log('⚠️ Event ignored - monitoring not active:', type);
    return;
  }
  
  console.log('🔵 Logging event:', type, '|', details);
  
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      type: type,
      details: details,
      examSessionId: examSessionId,
      timestamp: new Date().toISOString()
    }
  }, (response) => {
    if (chrome.runtime.lastError) {
      console.error(`❌ Failed to log event ${type}:`, chrome.runtime.lastError.message);
      return;
    }
    
    if (response && response.success) {
      console.log(`✅ Event logged successfully: ${type}`);
    } else {
      console.error(`❌ Failed to log event: ${type}`, response?.error || 'Unknown error');
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
    // ALWAYS prevent the action first
    e.preventDefault();
    e.stopPropagation();
    
    // Then log the attempt
    const selectedText = window.getSelection().toString().substring(0, 100); // First 100 chars
    logEvent('COPY', `Blocked copy attempt: "${selectedText}"`);
    
    // Show alert to user
    alert('⚠️ Copy is disabled during the exam!');
    return false;
  }
}, true);  // ← Capture phase for early interception

// Monitor paste operations
document.addEventListener('paste', (e) => {
  if (isMonitoring) {
    // ALWAYS prevent the action first
    e.preventDefault();
    e.stopPropagation();
    
    // Then log the attempt
    logEvent('PASTE', 'Blocked paste attempt');
    
    // Show alert to user
    alert('⚠️ Paste is disabled during the exam!');
    return false;
  }
}, true);  // ← Capture phase for early interception

// Monitor right-click (context menu)
document.addEventListener('contextmenu', (e) => {
  if (isMonitoring) {
    // ALWAYS prevent the action first
    e.preventDefault();
    e.stopPropagation();
    
    // Then log the attempt
    logEvent('RIGHT_CLICK', `Blocked right-click on: ${e.target.tagName}`);
    
    // Show alert to user
    alert('⚠️ Right-click is disabled during the exam!');
    return false;
  }
}, true);  // ← Capture phase for early interception

// Monitor keyboard shortcuts
document.addEventListener('keydown', (e) => {
  if (!isMonitoring) return;
  
  // BLOCK copy/paste/cut keyboard shortcuts
  if (e.ctrlKey || e.metaKey) {
    let combo = [];
    if (e.ctrlKey) combo.push('Ctrl');
    if (e.metaKey) combo.push('Cmd');
    if (e.shiftKey) combo.push('Shift');
    if (e.altKey) combo.push('Alt');
    combo.push(e.key.toUpperCase());
    
    const comboStr = combo.join('+');
    
    // Block copy, paste, cut operations
    if (e.key.toLowerCase() === 'c' || e.key.toLowerCase() === 'v' || e.key.toLowerCase() === 'x') {
      // ALWAYS prevent first
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      
      // Then log the blocked attempt
      logEvent('KEY_COMBINATION', `Blocked: ${comboStr}`);
      
      // Show alert
      alert(`⚠️ ${comboStr} is disabled during the exam!`);
      return false;
    }
    
    // Block find, new tab, new window
    if (e.key.toLowerCase() === 'f' || e.key.toLowerCase() === 't' || e.key.toLowerCase() === 'n' || e.key.toLowerCase() === 'w') {
      // ALWAYS prevent first
      e.preventDefault();
      e.stopPropagation();
      e.stopImmediatePropagation();
      
      // Then log the blocked attempt
      logEvent('KEY_COMBINATION', `Blocked: ${comboStr}`);
      
      // Show alert
      alert(`⚠️ ${comboStr} is disabled during the exam!`);
      return false;
    }
    
    // Log other suspicious combinations
    logEvent('KEY_COMBINATION', `Pressed: ${comboStr}`);
  }
  
  // Detect F12 (DevTools) and BLOCK it
  if (e.key === 'F12') {
    // ALWAYS prevent first
    e.preventDefault();
    e.stopPropagation();
    e.stopImmediatePropagation();
    
    // Then log the blocked attempt
    logEvent('BROWSER_DEVTOOLS', 'Blocked attempt to open browser developer tools');
    
    // Show alert
    alert('⚠️ Developer tools are disabled during the exam!');
    return false;
  }
  
  // Detect Escape (potential fullscreen exit)
  if (e.key === 'Escape') {
    logEvent('KEY_COMBINATION', 'Pressed Escape key (potential fullscreen exit)');
  }
}, true);  // ← Capture phase for early interception

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