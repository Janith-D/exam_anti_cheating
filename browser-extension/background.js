// Background service worker for Anti-Cheating Extension

// Cache for credentials to avoid repeated storage lookups
let credentialsCache = null;

// Load credentials into cache on startup
chrome.storage.local.get(['jwtToken', 'studentInfo'], (result) => {
  if (result.jwtToken) {
    credentialsCache = {
      jwtToken: result.jwtToken,
      studentInfo: result.studentInfo
    };
    console.log('Credentials loaded into cache');
  }
});

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
    console.log('📨 Received logEvent request:', request.data.type);
    
    // Handle async operation properly
    (async () => {
      try {
        const response = await logEventToBackend(request.data, request.token);
        console.log('✅ Event logged successfully to backend:', request.data.type);
        sendResponse({ success: true, data: response });
        
        // Increment event counter
        chrome.storage.local.get(['eventCount'], (result) => {
          const newCount = (result.eventCount || 0) + 1;
          chrome.storage.local.set({ eventCount: newCount }, () => {
            console.log('📊 Event count updated:', newCount);
          });
        });
      } catch (error) {
        console.error('❌ Error logging event to backend:', error);
        sendResponse({ success: false, error: error.message });
      }
    })();
    return true; // Keep message channel open for async response
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
    const credentials = {
      jwtToken: request.token,
      studentInfo: request.studentInfo
    };
    
    chrome.storage.local.set(credentials, () => {
      // Update cache
      credentialsCache = credentials;
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
      // Try cache first
      if (credentialsCache && credentialsCache.jwtToken) {
        token = credentialsCache.jwtToken;
      } else {
        // Fall back to storage
        const result = await chrome.storage.local.get(['jwtToken', 'studentInfo']);
        token = result.jwtToken;
        // Update cache
        if (token) {
          credentialsCache = {
            jwtToken: token,
            studentInfo: result.studentInfo
          };
        }
      }
    }

    if (!token) {
      throw new Error('No JWT token found. Please save credentials in the popup first.');
    }

    // Get student info from cache or storage
    let studentInfo = credentialsCache?.studentInfo;
    if (!studentInfo) {
      const studentResult = await chrome.storage.local.get(['studentInfo']);
      studentInfo = studentResult.studentInfo;
      // Update cache
      if (studentInfo && credentialsCache) {
        credentialsCache.studentInfo = studentInfo;
      }
    }

    if (!studentInfo || !studentInfo.studentId) {
      throw new Error('No student information found. Please save credentials in the popup first.');
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
      // Clone the response so we can read it twice if needed
      const responseClone = response.clone();
      let errorText;
      try {
        const errorJson = await response.json();
        errorText = JSON.stringify(errorJson);
        console.error('Backend error response:', errorJson);
      } catch (e) {
        // Use the cloned response if JSON parsing fails
        try {
          errorText = await responseClone.text();
          console.error('Backend error text:', errorText);
        } catch (e2) {
          errorText = `Status: ${response.status}`;
        }
      }
      
      // Special handling for 401 Unauthorized
      if (response.status === 401) {
        console.warn('JWT token expired or invalid. Please login again.');
        throw new Error('HTTP 401: Authentication failed. Please save your credentials again in the extension popup.');
      }
      
      throw new Error(`HTTP ${response.status}: ${errorText}`);
    }

    const result = await response.json();
    console.log('Event logged successfully:', result);
    return result;

  } catch (error) {
    console.error('Error in logEventToBackend:', error);
    console.error('Event data was:', eventData);
    throw error;
  }
}

// Detect tab switches
chrome.tabs.onActivated.addListener((activeInfo) => {
  chrome.storage.local.get(['isMonitoring'], (result) => {
    if (result.isMonitoring) {
      // Use logEventToBackend with cached credentials (no token parameter)
      logEventToBackend({
        type: 'TAB_SWITCH',
        details: 'User switched to another tab'
      }).catch(err => {
        // Don't log 401 errors (user might not be logged in yet)
        if (!err.message.includes('401')) {
          console.error('Tab switch event error:', err);
        }
      });
    }
  });
});

// Detect window focus changes
chrome.windows.onFocusChanged.addListener((windowId) => {
  if (windowId === chrome.windows.WINDOW_ID_NONE) {
    chrome.storage.local.get(['isMonitoring'], (result) => {
      if (result.isMonitoring) {
        // Use logEventToBackend with cached credentials (no token parameter)
        logEventToBackend({
          type: 'WINDOW_BLUR',
          details: 'User switched to another window or application'
        }).catch(err => {
          // Don't log 401 errors (user might not be logged in yet)
          if (!err.message.includes('401')) {
            console.error('Window blur event error:', err);
          }
        });
      }
    });
  }
});