// Popup script for Anti-Cheating Extension

document.addEventListener('DOMContentLoaded', () => {
  const statusElement = document.getElementById('status');
  const statusIndicator = document.getElementById('statusIndicator');
  const eventCountElement = document.getElementById('eventCount');
  const studentInfoSection = document.getElementById('studentInfo');
  const studentNameElement = document.getElementById('studentName');
  const studentIdElement = document.getElementById('studentId');
  const studentRoleElement = document.getElementById('studentRole');
  const startBtn = document.getElementById('startBtn');
  const stopBtn = document.getElementById('stopBtn');
  const loginBtn = document.getElementById('loginBtn');
  const messageElement = document.getElementById('message');

  // Load and display current status
  function updateStatus() {
    chrome.runtime.sendMessage({ action: "getStatus" }, (response) => {
      if (response) {
        // Update status
        statusElement.textContent = response.status;
        eventCountElement.textContent = response.eventCount || 0;
        
        // Update status indicator
        if (response.status === 'Monitoring Active') {
          statusIndicator.className = 'status-indicator status-active';
          startBtn.disabled = true;
          stopBtn.disabled = false;
        } else {
          statusIndicator.className = 'status-indicator status-inactive';
          startBtn.disabled = false;
          stopBtn.disabled = true;
        }
        
        // Update student info if available
        if (response.studentInfo) {
          studentInfoSection.style.display = 'block';
          studentNameElement.textContent = response.studentInfo.userName || '-';
          studentIdElement.textContent = response.studentInfo.studentId || '-';
          studentRoleElement.textContent = response.studentInfo.role || '-';
        } else {
          studentInfoSection.style.display = 'none';
        }
      }
    });
  }

  // Show message
  function showMessage(text, type = 'success') {
    messageElement.textContent = text;
    messageElement.className = `message show ${type}`;
    setTimeout(() => {
      messageElement.className = 'message';
    }, 3000);
  }

  // Start monitoring button
  startBtn.addEventListener('click', () => {
    // Check if credentials exist first
    chrome.storage.local.get(['jwtToken', 'studentInfo'], (result) => {
      if (!result.jwtToken || !result.studentInfo) {
        showMessage('Please login first with Student ID and JWT Token', 'error');
        return;
      }
      
      // Get exam session ID from input or use default
      const examSessionIdInput = document.getElementById('examSessionId');
      const examSessionId = examSessionIdInput ? examSessionIdInput.value : result.studentInfo.studentId;
      
      if (!examSessionId) {
        showMessage('Please enter Exam Session ID', 'error');
        return;
      }
      
      // Save exam session ID to storage
      chrome.storage.local.set({ 
        isMonitoring: true,
        examSessionId: examSessionId 
      }, () => {
        // Send message to background
        chrome.runtime.sendMessage({ action: "startMonitoring" }, (response) => {
          if (response && response.success) {
            showMessage('Monitoring started successfully!', 'success');
            updateStatus();
            
            // Notify all tabs to start monitoring
            chrome.tabs.query({}, (tabs) => {
              tabs.forEach((tab) => {
                chrome.tabs.sendMessage(tab.id, { 
                  action: "startMonitoring" 
                }).catch(() => {
                  // Ignore errors for tabs without content script
                });
                
                // Also set exam session in each tab
                chrome.tabs.sendMessage(tab.id, { 
                  action: "setExamSession",
                  examSessionId: examSessionId
                }).catch(() => {});
              });
            });
          } else {
            showMessage('Failed to start monitoring', 'error');
          }
        });
      });
    });
  });

  // Stop monitoring button
  stopBtn.addEventListener('click', () => {
    // Update storage first
    chrome.storage.local.set({ 
      isMonitoring: false 
    }, () => {
      // Send message to background
      chrome.runtime.sendMessage({ action: "stopMonitoring" }, (response) => {
        if (response && response.success) {
          showMessage('Monitoring stopped', 'success');
          updateStatus();
          
          // Notify all tabs to stop monitoring
          chrome.tabs.query({}, (tabs) => {
            tabs.forEach((tab) => {
              chrome.tabs.sendMessage(tab.id, { action: "stopMonitoring" }).catch(() => {
                // Ignore errors for tabs without content script
              });
            });
          });
        } else {
          showMessage('Failed to stop monitoring', 'error');
        }
      });
    });
  });

  // Quick login button
  loginBtn.addEventListener('click', () => {
    const studentId = document.getElementById('quickStudentId').value.trim();
    const token = document.getElementById('quickToken').value.trim();

    if (!studentId || !token) {
      showMessage('Please enter both Student ID and JWT Token', 'error');
      return;
    }

    // Try to decode JWT to get student info
    try {
      const payloadBase64 = token.split('.')[1];
      const payloadJson = atob(payloadBase64);
      const payload = JSON.parse(payloadJson);

      const studentInfo = {
        studentId: studentId,
        userName: payload.sub || 'Unknown',
        role: payload.roles ? payload.roles[0] : 'STUDENT',
        email: payload.email || ''
      };

      // Save credentials
      chrome.runtime.sendMessage({
        action: "setCredentials",
        token: token,
        studentInfo: studentInfo
      }, (response) => {
        if (response && response.success) {
          showMessage('Credentials saved successfully!', 'success');
          updateStatus();
          // Clear input fields
          document.getElementById('quickStudentId').value = '';
          document.getElementById('quickToken').value = '';
        } else {
          showMessage('Failed to save credentials', 'error');
        }
      });

    } catch (error) {
      showMessage('Invalid JWT token format', 'error');
      console.error('JWT decode error:', error);
    }
  });

  // Initial status update
  updateStatus();

  // Auto-refresh status every 2 seconds
  setInterval(updateStatus, 2000);
});