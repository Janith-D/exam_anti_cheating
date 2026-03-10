"""
Desktop Monitoring Application - GUI with System Tray
Runs in the background and can be launched via custom protocol
"""

import sys
import time
import threading
import logging
from pathlib import Path
import pystray
from PIL import Image, ImageDraw
import win32api
import win32con

from config import Config
from monitor import DesktopMonitor

logger = logging.getLogger(__name__)


class MonitorApp:
    """Desktop monitoring application with system tray"""
    
    def __init__(self):
        self.monitor = None
        self.icon = None
        self.is_running = False
        
    def create_icon_image(self, color="blue"):
        """Create a simple icon image"""
        # Create an image for the icon
        width = 64
        height = 64
        image = Image.new('RGB', (width, height), color='white')
        dc = ImageDraw.Draw(image)
        
        # Draw a circle
        if color == "blue":
            fill_color = (0, 0, 255)
        elif color == "green":
            fill_color = (0, 255, 0)
        elif color == "red":
            fill_color = (255, 0, 0)
        else:
            fill_color = (128, 128, 128)
        
        dc.ellipse([8, 8, 56, 56], fill=fill_color, outline='black')
        
        return image
    
    def start_monitoring(self, token, student_id, exam_session_id=None):
        """Start monitoring with given credentials"""
        if self.monitor and self.monitor.is_monitoring:
            logger.warning("Monitoring already active")
            return
        
        try:
            self.monitor = DesktopMonitor(token, student_id, exam_session_id)
            self.monitor.start_monitoring()
            self.is_running = True
            
            # Update icon to green (monitoring active)
            if self.icon:
                self.icon.icon = self.create_icon_image("green")
                self.icon.title = f"{Config.APP_NAME} - Monitoring Active"
            
            logger.info("Monitoring started successfully")
            self.show_notification("Monitoring Started", "Desktop monitoring is now active")
            
        except Exception as e:
            logger.error(f"Failed to start monitoring: {e}")
            self.show_notification("Error", f"Failed to start monitoring: {e}")
    
    def stop_monitoring(self):
        """Stop monitoring"""
        if self.monitor:
            self.monitor.stop_monitoring()
            self.is_running = False
            
            # Update icon to blue (monitoring inactive)
            if self.icon:
                self.icon.icon = self.create_icon_image("blue")
                self.icon.title = f"{Config.APP_NAME} - Inactive"
            
            logger.info("Monitoring stopped")
            self.show_notification("Monitoring Stopped", "Desktop monitoring has been stopped")
    
    def show_notification(self, title, message):
        """Show system tray notification"""
        if self.icon:
            self.icon.notify(message, title)
    
    def on_quit(self, icon, item):
        """Handle quit action"""
        logger.info("Quit requested")
        self.stop_monitoring()
        icon.stop()
    
    def on_start(self, icon, item):
        """Handle start monitoring action"""
        # This is a placeholder - actual start requires token from web
        self.show_notification("Info", "Please login through the web portal to start monitoring")
    
    def on_stop(self, icon, item):
        """Handle stop monitoring action"""
        self.stop_monitoring()
    
    def on_status(self, icon, item):
        """Show status"""
        status = "Active" if self.is_running else "Inactive"
        self.show_notification("Status", f"Monitoring is {status}")
    
    def create_menu(self):
        """Create system tray menu"""
        return pystray.Menu(
            pystray.MenuItem("Status", self.on_status),
            pystray.MenuItem("Stop Monitoring", self.on_stop, enabled=lambda item: self.is_running),
            pystray.MenuItem("Quit", self.on_quit)
        )
    
    def run_tray(self):
        """Run the system tray icon"""
        icon_image = self.create_icon_image("blue")
        self.icon = pystray.Icon(
            Config.APP_NAME,
            icon_image,
            f"{Config.APP_NAME} - Inactive",
            self.create_menu()
        )
        
        # Run the icon
        self.icon.run()
    
    def handle_protocol_launch(self, url):
        """Handle launch from custom protocol URL
        
        Expected format: desktop-monitor://login?token=JWT_TOKEN&studentId=123&sessionId=456
        """
        try:
            logger.info(f"Protocol launch: {url}")
            
            # Parse URL
            if not url.startswith("desktop-monitor://"):
                logger.error(f"Invalid protocol URL: {url}")
                return
            
            # Extract parameters
            params_str = url.split("?", 1)[1] if "?" in url else ""
            params = {}
            
            for param in params_str.split("&"):
                if "=" in param:
                    key, value = param.split("=", 1)
                    params[key] = value
            
            token = params.get("token")
            student_id = params.get("studentId")
            session_id = params.get("sessionId")
            
            if not token or not student_id:
                logger.error("Missing required parameters (token, studentId)")
                self.show_notification("Error", "Missing authentication information")
                return
            
            # Start monitoring
            self.start_monitoring(
                token, 
                int(student_id), 
                int(session_id) if session_id else None
            )
            
        except Exception as e:
            logger.error(f"Error handling protocol launch: {e}")
            self.show_notification("Error", f"Failed to start monitoring: {e}")


def parse_command_line():
    """Parse command line arguments"""
    import argparse
    
    parser = argparse.ArgumentParser(description='Desktop Monitoring Application')
    parser.add_argument('--url', help='Protocol URL for launch')
    parser.add_argument('--token', help='JWT authentication token')
    parser.add_argument('--student-id', type=int, help='Student ID')
    parser.add_argument('--session-id', type=int, help='Exam session ID (optional)')
    
    return parser.parse_args()


def main():
    """Main entry point"""
    args = parse_command_line()
    
    app = MonitorApp()
    
    # Handle different launch modes
    if args.url:
        # Launched via protocol handler
        logger.info(f"Launched via protocol: {args.url}")
        
        # Start tray in background thread
        tray_thread = threading.Thread(target=app.run_tray, daemon=True)
        tray_thread.start()
        
        # Give tray time to initialize
        time.sleep(2)
        
        # Handle protocol launch
        app.handle_protocol_launch(args.url)
        
        # Keep main thread alive
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("Shutting down...")
            app.stop_monitoring()
            
    elif args.token and args.student_id:
        # Launched with direct parameters
        logger.info("Launched with direct parameters")
        
        # Start tray in background thread
        tray_thread = threading.Thread(target=app.run_tray, daemon=True)
        tray_thread.start()
        
        # Give tray time to initialize
        time.sleep(2)
        
        # Start monitoring
        app.start_monitoring(args.token, args.student_id, args.session_id)
        
        # Keep main thread alive
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("Shutting down...")
            app.stop_monitoring()
            
    else:
        # Normal tray application mode
        logger.info("Starting in tray mode")
        app.run_tray()


if __name__ == "__main__":
    main()
