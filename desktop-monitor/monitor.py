"""
Desktop Monitoring Service
Monitors desktop activity, captures screenshots, and logs suspicious behavior
"""

import time
import threading
import logging
import sys
from datetime import datetime
from pathlib import Path
import pyautogui
import psutil
import win32gui
import win32process
import requests
from PIL import Image
import io
import json

from config import Config

# Configure logging
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s - %(levelname)s - %(message)s',
    handlers=[
        logging.FileHandler(Config.LOG_FILE),
        logging.StreamHandler(sys.stdout)
    ]
)
logger = logging.getLogger(__name__)


class DesktopMonitor:
    """Main desktop monitoring service"""
    
    def __init__(self, token, student_id, exam_session_id=None):
        self.token = token
        self.student_id = student_id
        self.exam_session_id = exam_session_id
        self.is_monitoring = False
        self.monitoring_thread = None
        self.screenshot_thread = None
        self.last_active_window = None
        
        Config.ensure_directories()
        logger.info(f"Desktop Monitor initialized for student {student_id}")
    
    def authenticate(self):
        """Authenticate with backend server"""
        try:
            url = f"{Config.API_BASE_URL}/authenticate"
            response = requests.post(
                url,
                json={"token": self.token},
                headers={"Content-Type": "application/json"}
            )
            
            if response.status_code == 200:
                data = response.json()
                logger.info(f"Authentication successful: {data.get('username')}")
                return True
            else:
                logger.error(f"Authentication failed: {response.status_code} - {response.text}")
                return False
                
        except Exception as e:
            logger.error(f"Authentication error: {e}")
            return False
    
    def start_monitoring(self):
        """Start monitoring desktop activity"""
        if self.is_monitoring:
            logger.warning("Monitoring already active")
            return
        
        if not self.authenticate():
            logger.error("Cannot start monitoring - authentication failed")
            return
        
        self.is_monitoring = True
        logger.info("Starting desktop monitoring")
        
        # Log monitoring start
        self.log_activity("MONITORING_STARTED", "Desktop monitoring application started", severity=1)
        
        # Start monitoring threads
        self.monitoring_thread = threading.Thread(target=self._monitor_activity_loop, daemon=True)
        self.screenshot_thread = threading.Thread(target=self._screenshot_loop, daemon=True)
        
        self.monitoring_thread.start()
        self.screenshot_thread.start()
        
        logger.info("Monitoring threads started")
    
    def stop_monitoring(self):
        """Stop monitoring"""
        if not self.is_monitoring:
            return
        
        self.is_monitoring = False
        logger.info("Stopping desktop monitoring")
        
        # Log monitoring stop
        self.log_activity("MONITORING_STOPPED", "Desktop monitoring application stopped", severity=1)
        
        # Wait for threads to finish
        if self.monitoring_thread:
            self.monitoring_thread.join(timeout=5)
        if self.screenshot_thread:
            self.screenshot_thread.join(timeout=5)
        
        logger.info("Monitoring stopped")
    
    def _monitor_activity_loop(self):
        """Monitor desktop activity in a loop"""
        while self.is_monitoring:
            try:
                # Get active window
                active_window = self.get_active_window()
                
                # Check if window changed
                if active_window != self.last_active_window:
                    logger.info(f"Window changed: {active_window}")
                    self.on_window_change(active_window, self.last_active_window)
                    self.last_active_window = active_window
                
                # Check for suspicious applications
                self.check_suspicious_apps()
                
                time.sleep(Config.ACTIVITY_LOG_INTERVAL)
                
            except Exception as e:
                logger.error(f"Error in activity monitoring: {e}")
                time.sleep(Config.ACTIVITY_LOG_INTERVAL)
    
    def _screenshot_loop(self):
        """Capture screenshots in a loop"""
        while self.is_monitoring:
            try:
                self.capture_and_upload_screenshot()
                time.sleep(Config.SCREENSHOT_INTERVAL)
                
            except Exception as e:
                logger.error(f"Error in screenshot capture: {e}")
                time.sleep(Config.SCREENSHOT_INTERVAL)
    
    def get_active_window(self):
        """Get the currently active window title and process name"""
        try:
            hwnd = win32gui.GetForegroundWindow()
            window_title = win32gui.GetWindowText(hwnd)
            
            # Get process name
            _, pid = win32process.GetWindowThreadProcessId(hwnd)
            process = psutil.Process(pid)
            process_name = process.name()
            
            return f"{window_title} ({process_name})"
            
        except Exception as e:
            logger.error(f"Error getting active window: {e}")
            return "Unknown"
    
    def get_running_processes(self):
        """Get list of running processes"""
        try:
            processes = []
            for proc in psutil.process_iter(['pid', 'name']):
                try:
                    processes.append(proc.info['name'])
                except:
                    pass
            return ", ".join(processes[:50])  # Limit to first 50
        except Exception as e:
            logger.error(f"Error getting running processes: {e}")
            return ""
    
    def check_suspicious_apps(self):
        """Check for suspicious applications running"""
        try:
            active_window = self.get_active_window().lower()
            
            for app in Config.SUSPICIOUS_APPS:
                if app in active_window:
                    severity = 5 if app in Config.BLOCKED_APPS else 4
                    logger.warning(f"Suspicious app detected: {app}")
                    self.log_activity(
                        "SUSPICIOUS_APP_DETECTED",
                        f"Detected suspicious application: {app}",
                        active_window=self.get_active_window(),
                        application_name=app,
                        severity=severity
                    )
                    break
                    
        except Exception as e:
            logger.error(f"Error checking suspicious apps: {e}")
    
    def on_window_change(self, new_window, old_window):
        """Handle window change event"""
        # Check if exam window was minimized
        if old_window and "exam" in old_window.lower() and "exam" not in new_window.lower():
            logger.warning("Exam window minimized or switched away")
            self.log_activity(
                "EXAM_WINDOW_MINIMIZED",
                f"Student switched from exam window to: {new_window}",
                active_window=new_window,
                severity=4
            )
        
        # Log window switch
        self.log_activity(
            "WINDOW_SWITCH",
            f"Window switched from '{old_window}' to '{new_window}'",
            active_window=new_window,
            severity=2
        )
    
    def capture_and_upload_screenshot(self):
        """Capture screenshot and upload to backend"""
        try:
            logger.info("Capturing screenshot...")
            
            # Capture screenshot
            screenshot = pyautogui.screenshot()
            
            # Save to temporary file
            timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
            temp_file = Config.TEMP_SCREENSHOT_DIR / f"screenshot_{timestamp}.png"
            screenshot.save(temp_file)
            
            # Get current context
            active_window = self.get_active_window()
            running_processes = self.get_running_processes()
            
            # Upload to backend
            success = self.upload_screenshot(temp_file, active_window, running_processes)
            
            if success:
                logger.info("Screenshot uploaded successfully")
                # Delete temporary file
                temp_file.unlink()
            else:
                logger.error("Failed to upload screenshot")
                
        except Exception as e:
            logger.error(f"Error capturing screenshot: {e}")
    
    def upload_screenshot(self, file_path, active_window, running_processes):
        """Upload screenshot to backend"""
        try:
            url = f"{Config.API_BASE_URL}/screenshot"
            
            with open(file_path, 'rb') as f:
                files = {'file': ('screenshot.png', f, 'image/png')}
                data = {
                    'studentId': self.student_id,
                    'activeWindow': active_window,
                    'runningProcesses': running_processes,
                    'captureSource': 'desktop'
                }
                
                if self.exam_session_id:
                    data['examSessionId'] = self.exam_session_id
                
                headers = {'Authorization': f'Bearer {self.token}'}
                
                response = requests.post(url, files=files, data=data, headers=headers)
                
                if response.status_code == 200:
                    result = response.json()
                    if result.get('flagged'):
                        logger.warning(f"Screenshot flagged as suspicious: {result.get('reason')}")
                    return True
                else:
                    logger.error(f"Upload failed: {response.status_code} - {response.text}")
                    return False
                    
        except Exception as e:
            logger.error(f"Error uploading screenshot: {e}")
            return False
    
    def log_activity(self, activity_type, details, active_window=None, application_name=None, severity=1):
        """Log desktop activity to backend"""
        try:
            url = f"{Config.API_BASE_URL}/activity"
            
            payload = {
                'studentId': self.student_id,
                'activityType': activity_type,
                'details': details,
                'severityLevel': severity
            }
            
            if self.exam_session_id:
                payload['examSessionId'] = self.exam_session_id
            
            if active_window:
                payload['activeWindow'] = active_window
            
            if application_name:
                payload['applicationName'] = application_name
            
            headers = {
                'Authorization': f'Bearer {self.token}',
                'Content-Type': 'application/json'
            }
            
            response = requests.post(url, json=payload, headers=headers)
            
            if response.status_code != 200:
                logger.error(f"Failed to log activity: {response.status_code} - {response.text}")
                
        except Exception as e:
            logger.error(f"Error logging activity: {e}")


def main():
    """Main entry point for command-line testing"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Desktop Monitoring Application')
    parser.add_argument('--token', required=True, help='JWT authentication token')
    parser.add_argument('--student-id', required=True, type=int, help='Student ID')
    parser.add_argument('--session-id', type=int, help='Exam session ID (optional)')
    
    args = parser.parse_args()
    
    monitor = DesktopMonitor(args.token, args.student_id, args.session_id)
    monitor.start_monitoring()
    
    try:
        # Keep running until interrupted
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        logger.info("Shutting down...")
        monitor.stop_monitoring()


if __name__ == "__main__":
    main()
