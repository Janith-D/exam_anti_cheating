window.addEventListener('blur', () => {
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      studentId: 1,
      type: "TAB_SWITCH",
      details: "Switched tab",
      examSessionId: 1
    },
    token: localStorage.getItem('jwt')
  });
});

document.addEventListener('copy', (e) => {
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      studentId: 1,
      type: "COPY",
      details: "Copied text",
      examSessionId: 1
    },
    token: localStorage.getItem('jwt')
  });
});

document.addEventListener('paste', (e) => {
  chrome.runtime.sendMessage({
    action: "logEvent",
    data: {
      studentId: 1,
      type: "PASTE",
      details: "Pasted text",
      examSessionId: 1
    },
    token: localStorage.getItem('jwt')
  });
});