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
  student?: {
    id: number;
    userName: string;
    firstName: string;
    lastName: string;
    studentId: string;
  };
  examSession?: {
    id: number;
    examName: string;
    status: string;
  };
  filePath: string;
  timestamp: string;
  activeWindow?: string;
  runningProcesses?: string;
  captureSource: string;
  flaggedSuspicious: boolean;
  suspiciousReason?: string;
  imageUrl?: string;
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
  private localMonitorUrl = 'http://127.0.0.1:5252';  // Local desktop monitor API

  constructor(
    private http: HttpClient,
    private authService: AuthService
  ) { }

  /**
   * Launch the desktop monitoring application via local HTTP API.
   * The desktop monitor must already be running in the background.
   * Falls back to custom protocol handler if local API is unavailable.
   */
  launchDesktopMonitor(studentId: number, examSessionId?: number, isEnrollment: boolean = false): boolean {
    try {
      const token = this.authService.getToken();
      if (!token) {
        console.error('No authentication token available');
        return false;
      }

      const mode = isEnrollment ? 'enrollment' : 'authenticated';
      const payload: any = {
        token: token,
        studentId: studentId,
        mode: mode
      };

      if (examSessionId) {
        payload.sessionId = examSessionId;
      }

      console.log('Sending start command to desktop monitor local API:', { studentId, mode });

      // Try local HTTP API first (desktop monitor already running in background)
      fetch(`${this.localMonitorUrl}/start`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(payload)
      }).then(response => {
        if (response.ok) {
          console.log('Desktop monitor started successfully via local API');
        } else {
          console.warn('Desktop monitor local API returned error, falling back to protocol handler');
          this._launchViaProtocol(token, studentId, examSessionId, mode);
        }
      }).catch(err => {
        console.warn('Desktop monitor local API not available, falling back to protocol handler:', err.message);
        this._launchViaProtocol(token, studentId, examSessionId, mode);
      });

      return true;
    } catch (error) {
      console.error('Error launching desktop monitor:', error);
      return false;
    }
  }

  /**
   * Fallback: launch via custom protocol handler (requires protocol registration)
   */
  private _launchViaProtocol(token: string, studentId: number, examSessionId?: number, mode?: string): void {
    let url = `desktop-monitor://login?token=${encodeURIComponent(token)}&studentId=${studentId}`;

    if (examSessionId) {
      url += `&sessionId=${examSessionId}`;
    }

    if (mode) {
      url += `&mode=${mode}`;
    }

    console.log('Launching desktop monitor via protocol:', url);

    const link = document.createElement('a');
    link.href = url;
    link.style.display = 'none';
    document.body.appendChild(link);
    link.click();

    setTimeout(() => {
      document.body.removeChild(link);
    }, 500);
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
   * Get all screenshots
   */
  getAllScreenshots(): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(`${this.apiUrl}/screenshots/all`);
  }

  /**
   * Get flagged (suspicious) screenshots
   */
  getFlaggedScreenshots(): Observable<Screenshot[]> {
    return this.http.get<Screenshot[]>(`${this.apiUrl}/screenshots/flagged`);
  }

  /**
   * Clear all screenshots (Admin only)
   */
  clearAllScreenshots(): Observable<{ message: string; deletedCount: number }> {
    return this.http.delete<{ message: string; deletedCount: number }>(`${this.apiUrl}/screenshots/clear`);
  }

  /**
   * Download screenshot file as blob
   */
  getScreenshotUrl(screenshotId: number): string {
    return `${this.apiUrl}/screenshots/${screenshotId}/download`;
  }

  /**
   * Download screenshot as blob for display in img tags (with auth)
   */
  getScreenshotBlob(screenshotId: number): Observable<Blob> {
    return this.http.get(`${this.apiUrl}/screenshots/${screenshotId}/download`, {
      responseType: 'blob'
    });
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
