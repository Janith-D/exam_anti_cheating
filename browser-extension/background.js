// Background service worker for Anti-Cheating Extension

// Initialize extension state
chrome.runtime.onInstalled.addListener(() => {
  console.log('Anti-Cheating Extension installed');
  chrome.storage.local.set({ 
    isMonitoring: false,
    eventCount: 0 
  });
});

// Listen for tab updates
chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete') {
    chrome.storage.local.get(['isMonitoring'], (result) => {
      if (result.isMonitoring) {
        chrome.tabs.sendMessage(tabId, { action: "startMonitoring" });
      }
    });
  }
});

// Handle messages from content script
chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "logEvent") {
    logEventToBackend(request.data, request.token)
      .then(response => {
        sendResponse({ success: true, data: response });
        // Increment event counter
        chrome.storage.local.get(['eventCount'], (result) => {
          chrome.storage.local.set({ eventCount: (result.eventCount || 0) + 1 });
        });
      })
      .catch(error => {
        console.error('Error logging event:', error);
        sendResponse({ success: false, error: error.message });
      });
    return true; // Will respond asynchronously
  }

  if (request.action === "getStatus") {
    chrome.storage.local.get(['isMonitoring', 'eventCount', 'studentInfo'], (result) => {
      sendResponse({
        status: result.isMonitoring ? 'Monitoring Active' : 'Monitoring Inactive',
        eventCount: result.eventCount || 0,
        studentInfo: result.studentInfo || null
      });
    });
    return true;
  }

  if (request.action === "startMonitoring") {
    chrome.storage.local.set({ isMonitoring: true }, () => {
      sendResponse({ success: true });
    });
    return true;
  }

  if (request.action === "stopMonitoring") {
    chrome.storage.local.set({ isMonitoring: false }, () => {
      sendResponse({ success: true });
    });
    return true;
  }

  if (request.action === "setCredentials") {
    chrome.storage.local.set({
      jwtToken: request.token,
      studentInfo: request.studentInfo
    }, () => {
      sendResponse({ success: true });
    });
    return true;
  }
});

// Function to log event to backend
async function logEventToBackend(eventData, token) {
  try {
    // Get stored token if not provided
    if (!token) {
      const result = await chrome.storage.local.get(['jwtToken']);
      token = result.jwtToken;
    }

    if (!token) {
      throw new Error('No JWT token found. Please login first.');
    }

    // Get student info
    const studentResult = await chrome.storage.local.get(['studentInfo']);
    const studentInfo = studentResult.studentInfo;

    if (!studentInfo || !studentInfo.studentId) {
      throw new Error('No student information found. Please login first.');
    }

    // Create FormData (backend expects multipart/form-data)
    const formData = new FormData();
    formData.append('studentId', studentInfo.studentId);
    formData.append('type', eventData.type);
    
    if (eventData.details) {
      formData.append('details', eventData.details);
    }
    
    if (eventData.snapshotPath) {
      formData.append('snapshotPath', eventData.snapshotPath);
    }
    
    if (eventData.examSessionId) {
      formData.append('examSessionId', eventData.examSessionId);
    }

    // Send to backend
    const response = await fetch('http://localhost:8080/api/events/log', {
      method: 'POST',
      headers: {
        'Authorization': 'Bearer ' + token
        // Don't set Content-Type - browser will set it with boundary for FormData
      },
      body: formData
    });

    if (!response.ok) {
      const errorText = await response.text();
      throw new Error(`HTTP ${response.status}: ${errorText}`);
    }

    const result = await response.json();
    console.log('Event logged successfully:', result);
    return result;

  } catch (error) {
    console.error('Error in logEventToBackend:', error);
    throw error;
  }
}

// Detect tab switches
chrome.tabs.onActivated.addListener((activeInfo) => {
  chrome.storage.local.get(['isMonitoring', 'jwtToken'], (result) => {
    if (result.isMonitoring && result.jwtToken) {
      logEventToBackend({
        type: 'TAB_SWITCH',
        details: 'User switched to another tab'
      }, result.jwtToken).catch(err => console.error('Tab switch event error:', err));
    }
  });
});

// Detect window focus changes
chrome.windows.onFocusChanged.addListener((windowId) => {
  if (windowId === chrome.windows.WINDOW_ID_NONE) {
    chrome.storage.local.get(['isMonitoring', 'jwtToken'], (result) => {
      if (result.isMonitoring && result.jwtToken) {
        logEventToBackend({
          type: 'WINDOW_BLUR',
          details: 'User switched to another window or application'
        }, result.jwtToken).catch(err => console.error('Window blur event error:', err));
      }
    });
  }
});