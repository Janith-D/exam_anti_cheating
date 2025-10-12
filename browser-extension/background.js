chrome.tabs.onUpdated.addListener((tabId, changeInfo, tab) => {
  if (changeInfo.status === 'complete') {
    chrome.tabs.sendMessage(tabId, { action: "monitorEvents" });
  }
});

chrome.runtime.onMessage.addListener((request, sender, sendResponse) => {
  if (request.action === "logEvent") {
    fetch('http://localhost:8080/api/events/log', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer ' + request.token
      },
      body: JSON.stringify(request.data)
    }).then(response => sendResponse({success: response.ok}));
    return true;
  }
});