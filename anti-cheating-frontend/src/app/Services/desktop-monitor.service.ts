import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable } from 'rxjs';
import { AuthService } from './auth.service.service';

export interface DesktopMonitorStatus {
  status: string;
  message: string;
  timestamp: number;
}

export interface Screenshot {
  id: number;
  studentId: number;
  examSessionId?: number;
  filePath: string;
  timestamp: string;
  activeWindow?: string;
  runningProcesses?: string;
  captureSource: string;
  flaggedSuspicious: boolean;
  suspiciousReason?: string;
}

export interface DesktopActivity {
  id: number;
  studentId: number;
  examSessionId?: number;
  timestamp: string;
  activityType: string;
  details: string;
  activeWindow?: string;
  applicationName?: string;
  severityLevel: number;
  isProcessed: boolean;
}

@Injectable({
  providedIn: 'root'
})
export class DesktopMonitorService {
  private apiUrl = 'http://localhost:8080/api/desktop-monitor';

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) { }

  /**
   * Launch the desktop monitoring application
   * This triggers the custom protocol handler
   */
  launchDesktopMonitor(studentId: number, examSessionId?: number): boolean {
    try {
      const token = this.authService.getToken();
      if (!token) {
        console.error('No authentication token available');
        return false;
      }

      // Build protocol URL
      let url = `desktop-monitor://login?token=${encodeURIComponent(token)}&studentId=${studentId}`;
      
      if (examSessionId) {
        url += `&sessionId=${examSessionId}`;
      }

      console.log('Launching desktop monitor:', url);

      // Try to open the custom protocol URL
      window.location.href = url;

      return true;
    } catch (error) {
      console.error('Error launching desktop monitor:', error);
      return false;
    }
  }

  /**
   * Check if desktop monitoring API is available
   */
  checkStatus(): Observable<DesktopMonitorStatus> {
    return this.http.get<DesktopMonitorStatus>(`${this.apiUrl}/status`);
  }

  /**
   * Get screenshots for a student
   */
  getScreenshotsByStudent(studentId: number): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(`${this.apiUrl}/screenshots/student/${studentId}`);
  }

  /**
   * Get screenshots for an exam session
   */
  getScreenshotsBySession(examSessionId: number): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(`${this.apiUrl}/screenshots/session/${examSessionId}`);
  }

  /**
   * Get screenshots for a student in a specific exam session
   */
  getScreenshotsByStudentAndSession(studentId: number, examSessionId: number): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(
      `${this.apiUrl}/screenshots/student/${studentId}/session/${examSessionId}`
    );
  }

  /**
   * Get flagged (suspicious) screenshots
   */
  getFlaggedScreenshots(): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(`${this.apiUrl}/screenshots/flagged`);
  }

  /**
   * Download screenshot file
   */
  getScreenshotUrl(screenshotId: number): string {
    return `${this.apiUrl}/screenshots/${screenshotId}/download`;
  }

  /**
   * Get desktop activities for a student
   */
  getActivitiesByStudent(studentId: number): Observable<DesktopActivity[]> {
    return this.http.get<DesktopActivity[]>(`${this.apiUrl}/activities/student/${studentId}`);
  }

  /**
   * Get desktop activities for an exam session
   */
  getActivitiesBySession(examSessionId: number): Observable<DesktopActivity[]> {
    return this.http.get<DesktopActivity[]>(`${this.apiUrl}/activities/session/${examSessionId}`);
  }

  /**
   * Get high-severity activities
   */
  getHighSeverityActivities(): Observable<DesktopActivity[]> {
    return this.http.get<DesktopActivity[]>(`${this.apiUrl}/activities/high-severity`);
  }

  /**
   * Show notification about desktop monitor
   */
  showDesktopMonitorNotification(message: string) {
    // Check if browser supports notifications
    if (!('Notification' in window)) {
      console.log('This browser does not support notifications');
      return;
    }

    // Request permission if needed
    if (Notification.permission === 'granted') {
      new Notification('Desktop Monitor', {
        body: message,
        icon: '/assets/icon.png'
      });
    } else if (Notification.permission !== 'denied') {
      Notification.requestPermission().then(permission => {
        if (permission === 'granted') {
          new Notification('Desktop Monitor', {
            body: message,
            icon: '/assets/icon.png'
          });
        }
      });
    }
  }

  /**
   * Check if desktop monitor protocol is installed
   */
  async checkDesktopMonitorInstalled(): Promise<boolean> {
    try {
      // Try to check status endpoint to verify backend is ready
      const status = await this.checkStatus().toPromise();
      return status?.status === 'online';
    } catch (error) {
      console.error('Desktop monitor API not available:', error);
      return false;
    }
  }

  /**
   * Prompt user to install desktop monitor
   */
  promptInstallDesktopMonitor(): void {
    const message = `
      Desktop Monitoring Application Required
      
      To ensure exam integrity, please install the desktop monitoring application.
      
      Installation Steps:
      1. Navigate to: desktop-monitor/
      2. Run: install.bat
      3. Reload this page
      4. Launch the exam again
    `;

    alert(message);
  }
}
