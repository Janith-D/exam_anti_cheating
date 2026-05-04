"""
Desktop Monitoring Configuration
"""

import json
import os
from pathlib import Path

class Config:
    """Application configuration"""
    
    # API Configuration
    BACKEND_URL = "http://localhost:8080"
    API_BASE_URL = f"{BACKEND_URL}/api/desktop-monitor"
    
    # Monitoring Settings
    SCREENSHOT_INTERVAL = 30  # seconds - during exam (reduced for testing)
    ENROLLMENT_SCREENSHOT_INTERVAL = 5  # seconds (5 seconds) - during enrollment
    ACTIVITY_LOG_INTERVAL = 30  # seconds
    HEARTBEAT_INTERVAL = 60  # seconds
    
    # File Settings
    TEMP_SCREENSHOT_DIR = Path("temp_screenshots")
    LOG_FILE = Path("desktop_monitor.log")
    
    # Application Settings
    APP_NAME = "Exam Desktop Monitor"
    APP_VERSION = "1.0.0"
    LOCAL_API_PORT = 5252  # Local HTTP API for receiving commands from frontend
    
    # Security
    TOKEN_FILE = Path("token.txt")
    
    # Suspicious Applications (will trigger high-severity alerts)
    # Only flag apps that could directly enable cheating (remote access, screen sharing, AI tools)
    SUSPICIOUS_APPS = [
        "teamviewer", "anydesk", "chrome remote desktop",
        "ultraviewer", "rustdesk", "parsec",
        "chatgpt", "bard", "copilot",
    ]

    # Blocked Applications (will trigger immediate alerts)
    BLOCKED_APPS = [
        "teamviewer",
        "anydesk",
        "chrome remote desktop",
        "ultraviewer"
    ]
    
    @classmethod
    def ensure_directories(cls):
        """Create necessary directories"""
        cls.TEMP_SCREENSHOT_DIR.mkdir(exist_ok=True)
        
    @classmethod
    def load_token(cls):
        """Load authentication token from file"""
        if cls.TOKEN_FILE.exists():
            return cls.TOKEN_FILE.read_text().strip()
        return None
    
    @classmethod
    def save_token(cls, token):
        """Save authentication token to file"""
        cls.TOKEN_FILE.write_text(token)
    
    @classmethod
    def clear_token(cls):
        """Clear authentication token"""
        if cls.TOKEN_FILE.exists():
            cls.TOKEN_FILE.unlink()
