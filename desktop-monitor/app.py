"""
Desktop Monitoring Application - GUI with System Tray
Runs in the background and can be launched via custom protocol.
Also exposes a local HTTP API on port 5252 for frontend communication.
"""

import sys
import time
import threading
import logging
import json
from pathlib import Path
from http.server import HTTPServer, BaseHTTPRequestHandler
import pystray
from PIL import Image, ImageDraw
import win32api
import win32con

from config import Config
from monitor import DesktopMonitor

logger = logging.getLogger(__name__)

# Global reference to the MonitorApp instance (used by the local HTTP API)
_app_instance = None


class LocalAPIHandler(BaseHTTPRequestHandler):
    """HTTP request handler for local API commands from the frontend."""

    def do_OPTIONS(self):
        """Handle CORS preflight requests."""
        self.send_response(200)
        self._send_cors_headers()
        self.end_headers()

    def do_GET(self):
        """Handle GET requests."""
        if self.path == "/status":
            self._send_json(200, {
                "running": True,
                "monitoring": _app_instance.is_running if _app_instance else False,
                "mode": _app_instance.monitor.mode if (_app_instance and _app_instance.monitor) else None
            })
        else:
            self._send_json(404, {"error": "Not found"})

    def do_POST(self):
        """Handle POST requests."""
        if self.path == "/start":
            self._handle_start()
        elif self.path == "/stop":
            self._handle_stop()
        else:
            self._send_json(404, {"error": "Not found"})

    def _handle_start(self):
        """Handle start monitoring command from frontend."""
        try:
            content_length = int(self.headers.get("Content-Length", 0))
            body = self.rfile.read(content_length).decode("utf-8")
            data = json.loads(body) if body else {}

            token = data.get("token")
            student_id = data.get("studentId")
            session_id = data.get("sessionId")
            mode = data.get("mode", "authenticated")

            logger.info(f"Local API: start command - studentId={student_id}, mode={mode}")

            if not token or not student_id:
                self._send_json(400, {"error": "token and studentId are required"})
                return

            if _app_instance:
                # Run start_monitoring in a separate thread to not block the HTTP response
                threading.Thread(
                    target=_app_instance.start_monitoring,
                    args=(token, int(student_id), int(session_id) if session_id else None),
                    kwargs={"mode": mode},
                    daemon=True
                ).start()
                self._send_json(200, {"message": "Monitoring start initiated", "mode": mode})
            else:
                self._send_json(500, {"error": "Monitor app not initialized"})

        except Exception as e:
            logger.error(f"Local API start error: {e}")
            self._send_json(500, {"error": str(e)})

    def _handle_stop(self):
        """Handle stop monitoring command."""
        if _app_instance:
            _app_instance.stop_monitoring()
            self._send_json(200, {"message": "Monitoring stopped"})
        else:
            self._send_json(500, {"error": "Monitor app not initialized"})

    def _send_cors_headers(self):
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Access-Control-Allow-Methods", "GET, POST, OPTIONS")
        self.send_header("Access-Control-Allow-Headers", "Content-Type")

    def _send_json(self, status, data):
        self.send_response(status)
        self._send_cors_headers()
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        self.wfile.write(json.dumps(data).encode("utf-8"))

    def log_message(self, format, *args):
        """Override to use our logger instead of stderr."""
        logger.info(f"Local API: {args[0]}")


def run_local_api():
    """Run the local HTTP API server."""
    try:
        server = HTTPServer(("127.0.0.1", Config.LOCAL_API_PORT), LocalAPIHandler)
        logger.info(f"Local API server started on http://127.0.0.1:{Config.LOCAL_API_PORT}")
        server.serve_forever()
    except OSError as e:
        logger.error(f"Failed to start local API server: {e}")
        logger.error("Another instance may already be running on port %d", Config.LOCAL_API_PORT)
    except Exception as e:
        logger.error(f"Local API server error: {e}")


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
        elif color == "yellow":
            fill_color = (255, 255, 0)
        else:
            fill_color = (128, 128, 128)

        dc.ellipse([8, 8, 56, 56], fill=fill_color, outline='black')

        return image
    
    def start_monitoring(self, token, student_id, exam_session_id=None, mode=None):
        """Start monitoring with given credentials"""
        # If we have a token, this is an authenticated request - stop enrollment monitor first
        if token is not None and self.monitor and self.monitor.is_monitoring:
            logger.info("Stopping previous monitoring to start new session")
            self.monitor.stop_monitoring()
            time.sleep(1)

        if self.monitor and self.monitor.is_monitoring:
            logger.warning("Monitoring already active")
            return

        try:
            # Determine mode: use explicit mode if provided, otherwise infer from token
            if mode is None:
                mode = "enrollment" if token is None else "authenticated"

            self.monitor = DesktopMonitor(token, student_id, exam_session_id, mode=mode)
            success = self.monitor.start_monitoring()

            if not success:
                logger.error("Authentication failed - will retry in 10 seconds")
                self.show_notification("Auth Failed", "Will retry connection in 10 seconds...")
                # Update icon to red (failed)
                if self.icon:
                    self.icon.icon = self.create_icon_image("red")
                    self.icon.title = f"{Config.APP_NAME} - Auth Failed (retrying)"
                # Retry in background
                retry_thread = threading.Thread(
                    target=self._retry_monitoring,
                    args=(token, student_id, exam_session_id),
                    daemon=True
                )
                retry_thread.start()
                return

            self.is_running = True

            # Update icon to green (monitoring active) or yellow (enrollment mode)
            if self.icon:
                icon_color = "yellow" if mode == "enrollment" else "green"
                self.icon.icon = self.create_icon_image(icon_color)
                status_text = "Enrollment Mode" if mode == "enrollment" else "Monitoring Active"
                self.icon.title = f"{Config.APP_NAME} - {status_text}"

            logger.info(f"Monitoring started successfully in {mode} mode")
            self.show_notification(
                "Started",
                f"Desktop monitoring is now active ({mode} mode)"
            )

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

    def _retry_monitoring(self, token, student_id, exam_session_id, max_retries=5):
        """Retry monitoring connection with exponential backoff"""
        for attempt in range(1, max_retries + 1):
            wait_time = 10 * attempt  # 10s, 20s, 30s, 40s, 50s
            logger.info(f"Retry attempt {attempt}/{max_retries} in {wait_time}s...")
            time.sleep(wait_time)
            
            try:
                self.monitor = DesktopMonitor(token, student_id, exam_session_id)
                success = self.monitor.start_monitoring()
                
                if success:
                    self.is_running = True
                    if self.icon:
                        self.icon.icon = self.create_icon_image("green")
                        self.icon.title = f"{Config.APP_NAME} - Monitoring Active"
                    logger.info(f"Monitoring started on retry {attempt}")
                    self.show_notification("Monitoring Started", "Desktop monitoring is now active")
                    return
                else:
                    logger.warning(f"Retry {attempt} failed - auth still failing")
            except Exception as e:
                logger.error(f"Retry {attempt} error: {e}")
        
        logger.error(f"All {max_retries} retry attempts failed")
        self.show_notification("Connection Failed", "Could not connect to server. Please restart.")
    
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
            from urllib.parse import unquote

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
                    # URL-decode the parameter value
                    params[key] = unquote(value)

            token = params.get("token")
            student_id = params.get("studentId")
            session_id = params.get("sessionId")
            mode = params.get("mode", "authenticated")  # "enrollment" or "authenticated"

            if not token or not student_id:
                logger.error("Missing required parameters (token, studentId)")
                self.show_notification("Error", "Missing authentication information")
                return

            # Start monitoring with the specified mode
            self.start_monitoring(
                token,
                int(student_id),
                int(session_id) if session_id else None,
                mode=mode
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
    global _app_instance

    args = parse_command_line()

    app = MonitorApp()
    _app_instance = app

    # Always start the local HTTP API server for frontend communication
    api_thread = threading.Thread(target=run_local_api, daemon=True)
    api_thread.start()
    logger.info(f"Local API server starting on port {Config.LOCAL_API_PORT}")

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

        # Start tray in background thread
        tray_thread = threading.Thread(target=app.run_tray, daemon=False)
        tray_thread.start()

        # Give tray time to initialize
        time.sleep(2)

        # Start background monitoring (no authentication required, just captures enrollment screenshots)
        logger.info("Starting background monitoring for enrollment screenshots")
        app.start_monitoring(None, None, None)

        # Keep main thread alive
        try:
            while True:
                time.sleep(1)
        except KeyboardInterrupt:
            logger.info("Shutting down...")
            if app.monitor:
                app.monitor.stop_monitoring()


if __name__ == "__main__":
    main()
