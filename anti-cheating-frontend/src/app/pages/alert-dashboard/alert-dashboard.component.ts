import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { Subscription, interval } from 'rxjs';
import { AlertService } from '../../Services/alert.service.service';
import { EventService } from '../../Services/event.service.service';
import { ExamSessionService } from '../../Services/exam-session.service';
import { WebSocketService } from '../../Services/websocket.service.service';
import { StudentActivityService, StudentActivity } from '../../Services/student-activity.service';
import { AuthService } from '../../Services/auth.service.service';
import { Alert, AlertSeverity } from '../../models/alert.model';
import { Event } from '../../models/event.model';
import { ExamSession } from '../../models/exam-session.model';

@Component({
  selector: 'app-alert-dashboard',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './alert-dashboard.component.html',
  styleUrl: './alert-dashboard.component.css'
})
export class AlertDashboardComponent implements OnInit, OnDestroy {
  // Real-time data
  alerts: Alert[] = [];
  recentEvents: Event[] = [];
  activeSessions: ExamSession[] = [];
  studentActivities: StudentActivity[] = [];
  activeStudents: Set<number> = new Set(); // Track unique active students
  
  // Statistics
  stats = {
    totalStudentsInTest: 0,
    criticalAlerts: 0,
    activeTests: 0,
    totalAlerts: 0,
    recentActivities: 0
  };
  
  // Filters
  selectedSeverity: string = 'ALL';
  selectedStatus: string = 'ALL';
  
  // Subscriptions
  private alertSubscription: Subscription | null = null;
  private refreshSubscription: Subscription | null = null;
  private activitySubscription: Subscription | null = null;
  
  // Loading states
  loading = false;
  error = '';
  
  // Sound notification
  soundEnabled = true;

  constructor(
    private alertService: AlertService,
    private eventService: EventService,
    private examSessionService: ExamSessionService,
    private webSocketService: WebSocketService,
    private studentActivityService: StudentActivityService,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    // Wait for authentication to be ready
    // Check multiple times with increasing delays to ensure token is stored
    const checkAuth = (attempt: number = 0) => {
      const token = localStorage.getItem('token');
      console.log(`🔍 Auth check attempt ${attempt + 1}:`, token ? '✅ Token found' : '❌ No token');
      
      if (token && this.authService.isLoggedIn) {
        console.log('✅ Authentication verified, loading alert dashboard...');
        this.loadAllData();
        this.connectWebSocket();
        this.startAutoRefresh();
      } else if (attempt < 5) {
        // Try again with exponential backoff: 50ms, 100ms, 200ms, 400ms, 800ms
        const delay = 50 * Math.pow(2, attempt);
        console.log(`⏳ Retrying in ${delay}ms...`);
        setTimeout(() => checkAuth(attempt + 1), delay);
      } else {
        console.warn('❌ Authentication timeout. Redirecting to login...');
        this.router.navigate(['/login']);
      }
    };
    
    // Start checking after a small initial delay
    setTimeout(() => checkAuth(), 50);
  }

  ngOnDestroy(): void {
    if (this.alertSubscription) {
      this.alertSubscription.unsubscribe();
    }
    if (this.refreshSubscription) {
      this.refreshSubscription.unsubscribe();
    }
    if (this.activitySubscription) {
      this.activitySubscription.unsubscribe();
    }
    this.webSocketService.disconnect();
    this.studentActivityService.disconnect();
  }

  loadAllData(): void {
    this.loading = true;
    this.error = '';
    
    // Load active exam sessions
    this.loadActiveSessions();
    
    // Load all alerts
    this.loadAlerts();
    
    // Load recent events
    this.loadRecentEvents();
  }

  loadActiveSessions(): void {
    this.examSessionService.getActiveExamSessions().subscribe({
      next: (sessions: ExamSession[]) => {
        this.activeSessions = sessions;
        this.stats.activeTests = sessions.length;
        // Count unique students (would need actual enrollment data)
        this.stats.totalStudentsInTest = sessions.length * 10; // Placeholder
      },
      error: (error: any) => {
        console.error('Error loading active sessions:', error);
      }
    });
  }

  loadAlerts(): void {
    this.alertService.getAllAlerts().subscribe({
      next: (alerts: Alert[]) => {
        this.alerts = alerts.sort((a, b) => {
          const timeA = new Date(a.timestamp || a.createdAt || '').getTime();
          const timeB = new Date(b.timestamp || b.createdAt || '').getTime();
          return timeB - timeA;
        });
        
        this.stats.totalAlerts = alerts.length;
        this.stats.criticalAlerts = alerts.filter(a => 
          (a.severity === 'RED' || a.severity === 'CRITICAL' || a.severity === 'ORANGE' || a.severity === 'HIGH') &&
          (a.status !== 'RESOLVED' && !a.resolved)
        ).length;
        
        this.loading = false;
      },
      error: (error: any) => {
        console.error('Error loading alerts:', error);
        
        // Check if it's an authentication error
        if (error.status === 401) {
          this.error = '🔒 Authentication required. Please login as ADMIN to access this page.';
          // Redirect to login after 2 seconds
          setTimeout(() => {
            this.router.navigate(['/login']);
          }, 2000);
        } else {
          this.error = 'Failed to load alerts. Please try again.';
        }
        
        this.loading = false;
      }
    });
  }

  loadRecentEvents(): void {
    // Load events for all active sessions
    this.activeSessions.forEach(session => {
      // Use the test ID from the session; some session shapes use `testId` or `test?.id`
      const testId = (session as any).testId ?? (session as any).test?.id;
      if (testId) {
        this.eventService.getEventsByTest(testId).subscribe({
          next: (events: Event[]) => {
            this.recentEvents = [...this.recentEvents, ...events]
              .sort((a, b) => {
                const timeA = new Date(a.timestamp || (a as any).createdAt || '').getTime();
                const timeB = new Date(b.timestamp || (b as any).createdAt || '').getTime();
                return timeB - timeA;
              })
              .slice(0, 50); // Keep only recent 50 events
            
            this.stats.recentActivities = this.recentEvents.length;
          },
          error: (error: any) => {
            console.error('Error loading events:', error);
          }
        });
      }
    });
  }

  connectWebSocket(): void {
    console.log('🔌 Connecting to WebSocket for real-time updates...');
    
    // Connect to WebSocket service for alerts
    this.webSocketService.connect();
    
    // Subscribe to real-time alerts
    this.alertSubscription = this.webSocketService.getAlerts().subscribe({
      next: (alert: Alert | null) => {
        if (!alert) return;
        
        console.log('🚨 Real-time alert received:', alert);
        
        // Add to alerts list
        this.alerts.unshift(alert);
        this.stats.totalAlerts++;
        
        // Update critical alerts count
        if (alert.severity === 'CRITICAL' || alert.severity === 'HIGH') {
          this.stats.criticalAlerts++;
          
          // Play sound for critical alerts
          if (this.soundEnabled) {
            this.playNotificationSound();
          }
        }
      },
      error: (error: any) => {
        console.error('❌ Error receiving alerts:', error);
      }
    });
    
    // Connect to student activity WebSocket and subscribe
    console.log('📡 Connecting to Student Activity WebSocket...');
    this.studentActivityService.connect();
    
    // Wait a bit for connection to establish, then subscribe
    setTimeout(() => {
      console.log('📨 Subscribing to student activities...');
      this.activitySubscription = this.studentActivityService.subscribeToActivities().subscribe({
        next: (activity: StudentActivity) => {
          console.log('📨 Real-time student activity received:', activity);
          
          // Add to activities list (keep last 100)
          this.studentActivities.unshift(activity);
          if (this.studentActivities.length > 100) {
            this.studentActivities.pop();
          }
          
          // Track active students
          if (activity.studentId) {
            if (activity.activityType === 'TEST_STARTED') {
              this.activeStudents.add(activity.studentId);
              console.log('👤 Student joined:', activity.studentName, '| Total active:', this.activeStudents.size);
            } else if (activity.activityType === 'TEST_SUBMITTED') {
              this.activeStudents.delete(activity.studentId);
              console.log('👋 Student left:', activity.studentName, '| Total active:', this.activeStudents.size);
            } else {
              // Any other activity means student is active
              this.activeStudents.add(activity.studentId);
            }
          }
          
          // Update stats
          this.stats.totalStudentsInTest = this.activeStudents.size;
          this.stats.recentActivities = this.studentActivities.length;
          
          // Auto-create alert for HIGH and CRITICAL severity activities
          if (activity.severity === 'HIGH' || activity.severity === 'CRITICAL') {
            console.log('⚠️ Creating alert for suspicious activity:', activity.activityType);
            this.createAlertFromActivity(activity);
          }
        },
        error: (error: any) => {
          console.error('❌ Error receiving student activities:', error);
        }
      });
      
      console.log('✅ WebSocket subscriptions active');
    }, 1000);
  }

  startAutoRefresh(): void {
    // Refresh data every 10 seconds
    this.refreshSubscription = interval(10000).subscribe(() => {
      this.loadAllData();
    });
  }

  playAlertSound(): void {
    const audio = new Audio('data:audio/wav;base64,UklGRnoGAABXQVZFZm10IBAAAAABAAEAQB8AAEAfAAABAAgAZGF0YQoGAACBhYqFbF1fdJivrJBhNjVgodDbq2EcBj+a2/LDciUFLIHO8tiJNwgZaLvt559NEAxQp+PwtmMcBjiR1/LMeSwFJHfH8N2QQAoUXrTp66hVFApGn+DyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvmwhBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFApFnuDyvm0hBjiP1fPTfC8FJHbG8N+SQwoUX7Xp7KlXFA==');
    audio.play().catch(err => console.error('Error playing sound:', err));
  }

  showNotification(title: string, message: string): void {
    if ('Notification' in window) {
      if (Notification.permission === 'granted') {
        new Notification(title, { 
          body: message,
          icon: '/assets/alert-icon.png'
        });
      } else if (Notification.permission !== 'denied') {
        Notification.requestPermission().then(permission => {
          if (permission === 'granted') {
            new Notification(title, { body: message });
          }
        });
      }
    }
  }
  
  playNotificationSound(): void {
    this.playAlertSound(); // Reuse the same sound
  }
  
  showActivityNotification(activity: StudentActivity): void {
    const severityEmoji = activity.severity === 'CRITICAL' ? '🚨' : '⚠️';
    const title = `${severityEmoji} Student Activity - ${activity.severity}`;
    const message = `${activity.studentName}: ${activity.description}`;
    this.showNotification(title, message);
  }

  // Filter methods
  filterBySeverity(severity: string): void {
    this.selectedSeverity = severity;
  }

  filterByStatus(status: string): void {
    this.selectedStatus = status;
  }

  get filteredAlerts(): Alert[] {
    return this.alerts.filter(alert => {
      const severityMatch = this.selectedSeverity === 'ALL' || alert.severity === this.selectedSeverity;
      const statusMatch = this.selectedStatus === 'ALL' || 
        (this.selectedStatus === 'UNRESOLVED' && !alert.resolved && alert.status !== 'RESOLVED') ||
        (this.selectedStatus === 'RESOLVED' && (alert.resolved || alert.status === 'RESOLVED'));
      
      return severityMatch && statusMatch;
    });
  }

  // Alert actions
  resolveAlert(alertId: number | undefined): void {
    if (!alertId) return;
    
    this.alertService.resolveAlert(alertId).subscribe({
      next: () => {
        const alert = this.alerts.find(a => a.id === alertId);
        if (alert) {
          alert.resolved = true;
          alert.status = 'RESOLVED';
          this.stats.criticalAlerts--;
        }
      },
      error: (error: any) => {
        console.error('Error resolving alert:', error);
        this.error = 'Failed to resolve alert';
      }
    });
  }

  viewAlertDetails(alert: Alert): void {
    console.log('Alert details:', alert);
    // Could navigate to detailed alert page or show modal
  }

  viewStudentDetails(studentId: number): void {
    this.router.navigate(['/admin/students', studentId]);
  }

  // Create alert from suspicious activity
  createAlertFromActivity(activity: StudentActivity): void {
    // Map severity
    let alertSeverity: AlertSeverity = 'MEDIUM';
    if (activity.severity === 'HIGH') {
      alertSeverity = 'HIGH';
    } else if (activity.severity === 'CRITICAL') {
      alertSeverity = 'CRITICAL';
    } else if (activity.severity === 'LOW') {
      alertSeverity = 'LOW';
    }
    
    const alert: Alert = {
      student: {
        id: activity.studentId,
        username: activity.studentName || 'Unknown',
        email: '',
        role: 'STUDENT'
      },
      severity: alertSeverity,
      message: `${activity.activityType}: ${activity.description}`,
      timestamp: activity.timestamp || new Date().toISOString(),
      resolved: false,
      status: 'ACTIVE'
    };
    
    // Add to alerts list
    this.alerts.unshift(alert);
    this.stats.totalAlerts++;
    
    if (alertSeverity === 'CRITICAL' || alertSeverity === 'HIGH') {
      this.stats.criticalAlerts++;
      
      // Show notification for critical alerts
      this.showNotification(
        `🚨 ${alertSeverity} Alert`,
        `${activity.studentName}: ${activity.description}`
      );
      
      // Play sound
      if (this.soundEnabled) {
        this.playAlertSound();
      }
    }
    
    console.log('✅ Alert created from activity:', alert);
  }

  // Utility methods
  getSeverityClass(severity: string): string {
    const classes: { [key: string]: string } = {
      'RED': 'severity-red',
      'CRITICAL': 'severity-red',
      'ORANGE': 'severity-orange',
      'HIGH': 'severity-orange',
      'YELLOW': 'severity-yellow',
      'MEDIUM': 'severity-yellow',
      'GREEN': 'severity-green',
      'LOW': 'severity-green'
    };
    return classes[severity] || 'severity-default';
  }

  getSeverityIcon(severity: string): string {
    const icons: { [key: string]: string } = {
      'RED': '🔴',
      'CRITICAL': '🔴',
      'ORANGE': '🟠',
      'HIGH': '🟠',
      'YELLOW': '🟡',
      'MEDIUM': '🟡',
      'GREEN': '🟢',
      'LOW': '🟢'
    };
    return icons[severity] || '⚪';
  }

  getEventIcon(eventType: string): string {
    const icons: { [key: string]: string } = {
      'FACE_DETECTION': '👤',
      'TAB_SWITCH': '🔄',
      'COPY_PASTE': '📋',
      'MULTIPLE_FACES': '👥',
      'NO_FACE': '❌',
      'SUSPICIOUS': '⚠️',
      'TEST_START': '▶️',
      'TEST_END': '⏹️',
      'ANSWER_SUBMIT': '✅'
    };
    return icons[eventType] || '📌';
  }
  
  getActivityIcon(activityType: string): string {
    const icons: { [key: string]: string } = {
      'TEST_STARTED': '▶️',
      'TEST_SUBMITTED': '⏹️',
      'QUESTION_ANSWERED': '✏️',
      'TAB_SWITCH': '🔄',
      'COPY_ATTEMPT': '📋',
      'PASTE_ATTEMPT': '📌',
      'RIGHT_CLICK': '🖱️',
      'WINDOW_BLUR': '👁️',
      'WINDOW_FOCUS': '👀',
      'FACE_DETECTED': '👤',
      'MULTIPLE_FACES': '👥',
      'NO_FACE': '❌',
      'MOUSE_LEAVE': '🖱️'
    };
    return icons[activityType] || '📍';
  }

  toggleSound(): void {
    this.soundEnabled = !this.soundEnabled;
  }

  refresh(): void {
    this.loadAllData();
  }

  goBack(): void {
    this.router.navigate(['/admin/dashboard']);
  }
}
