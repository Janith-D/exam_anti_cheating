import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { Subscription } from 'rxjs';
import { AuthService } from '../../Services/auth.service.service';
import { WebSocketService } from '../../Services/websocket.service.service';
import { AlertService } from '../../Services/alert.service.service';
import { TestService } from '../../Services/test.service.service';
import { EventService } from '../../Services/event.service.service';
import { Alert } from '../../models/alert.model';
import { Test } from '../../models/test.model';
import { Event } from '../../models/event.model';

@Component({
  selector: 'app-proctor-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './proctor-dashboard.component.html',
  styleUrl: './proctor-dashboard.component.css'
})
export class ProctorDashboardComponent implements OnInit, OnDestroy {
  currentUser: any = null;
  activeTab: string = 'overview';
  
  // Real-time alerts
  alerts: Alert[] = [];
  alertSubscription: Subscription | null = null;
  unreadAlertsCount: number = 0;
  
  // Statistics
  stats = {
    totalTests: 0,
    activeExams: 0,
    totalStudents: 0,
    criticalAlerts: 0
  };
  
  // Recent activities
  recentEvents: Event[] = [];
  
  // Tests
  tests: Test[] = [];
  
  // Loading states
  loading = false;
  error = '';

  constructor(
    private authService: AuthService,
    private router: Router,
    private location: Location,
    private webSocketService: WebSocketService,
    private alertService: AlertService,
    private testService: TestService,
    private eventService: EventService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    
    // Wait for authentication to be ready
    // Check multiple times with increasing delays to ensure token is stored
    const checkAuth = (attempt: number = 0) => {
      const token = localStorage.getItem('token');
      const currentUser = localStorage.getItem('currentUser');
      const isLoggedIn = this.authService.isLoggedIn;
      const isAdmin = this.authService.isAdmin;
      
      console.log(`🔍 Auth check attempt ${attempt + 1}:`);
      console.log('  Token:', token ? `✅ Found (${token.substring(0, 30)}...)` : '❌ Not found');
      console.log('  User:', currentUser ? `✅ Found (${JSON.parse(currentUser).username})` : '❌ Not found');
      console.log('  isLoggedIn:', isLoggedIn);
      console.log('  isAdmin:', isAdmin);
      console.log('  AuthService.getToken():', this.authService.getToken() ? '✅ Has token' : '❌ No token');
      
      if (token && isLoggedIn) {
        console.log('✅ Authentication verified, loading dashboard...');
        // Add small delay to ensure interceptor is ready
        setTimeout(() => {
          this.loadDashboardData();
          this.connectWebSocket();
        }, 100);
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
    this.webSocketService.disconnect();
  }

  loadDashboardData(): void {
    this.loading = true;
    
    // Load tests
    this.testService.getAllTests().subscribe({
      next: (tests) => {
        this.tests = tests;
        this.stats.totalTests = tests.length;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading tests:', error);
        this.error = 'Failed to load dashboard data';
        this.loading = false;
      }
    });
    
    // Load alerts
    this.loadAlerts();
  }

  loadAlerts(): void {
    this.alertService.getAllAlerts().subscribe({
      next: (alerts: Alert[]) => {
        this.alerts = alerts.sort((a: Alert, b: Alert) => {
          return new Date(b.timestamp || b.createdAt || '').getTime() - new Date(a.timestamp || a.createdAt || '').getTime();
        });
        this.unreadAlertsCount = alerts.filter((a: Alert) => !a.resolved && a.status !== 'RESOLVED').length;
        this.stats.criticalAlerts = alerts.filter((a: Alert) => 
          a.severity === 'RED' || a.severity === 'ORANGE' || a.severity === 'HIGH' || a.severity === 'CRITICAL'
        ).length;
      },
      error: (error: any) => {
        console.error('Error loading alerts:', error);
        
        // Handle authentication errors gracefully
        if (error.status === 401) {
          console.warn('⚠️ Not authenticated. Please login as ADMIN.');
          // Don't show error to user, just log it
          // The adminGuard should handle redirecting to login
        }
      }
    });
  }

  connectWebSocket(): void {
    this.webSocketService.connect();
    
    // Subscribe to real-time alerts
    this.alertSubscription = this.webSocketService.getAlerts().subscribe({
      next: (alert: Alert | null) => {
        if (!alert) return; // Skip null values
        
        console.log('New alert received:', alert);
        this.alerts.unshift(alert);
        this.unreadAlertsCount++;
        this.stats.criticalAlerts++;
        
        // Show notification for critical alerts
        if (alert.severity === 'RED' || alert.severity === 'ORANGE') {
          this.showNotification('Critical Alert', alert.message || 'New suspicious activity detected');
        }
      },
      error: (error: any) => {
        console.error('WebSocket error:', error);
      }
    });
  }

  showNotification(title: string, message: string): void {
    if ('Notification' in window && Notification.permission === 'granted') {
      new Notification(title, { body: message });
    } else {
      alert(`${title}: ${message}`);
    }
  }

  setActiveTab(tab: string): void {
    this.activeTab = tab;
    
    if (tab === 'alerts' && this.unreadAlertsCount > 0) {
      this.unreadAlertsCount = 0;
    }
  }

  // Navigation methods
  navigateToTestManagement(): void {
    this.router.navigate(['/admin/test-management']);
  }

  navigateToAlerts(): void {
    this.router.navigate(['/admin/alerts']);
  }

  navigateToStudents(): void {
    this.router.navigate(['/admin/students']);
  }

  navigateToSessions(): void {
    this.router.navigate(['/admin/sessions']);
  }

  viewTestDetails(testId: number): void {
    this.router.navigate(['/admin/test-management', testId]);
  }

  viewAlertDetails(alertId: number): void {
    this.router.navigate(['/admin/alerts', alertId]);
  }

  resolveAlert(alertId: number): void {
    if (!alertId) return;
    
    this.alertService.resolveAlert(alertId).subscribe({
      next: () => {
        const alert = this.alerts.find(a => a.id === alertId);
        if (alert) {
          alert.resolved = true;
          alert.status = 'RESOLVED';
        }
        this.loadAlerts();
      },
      error: (error: any) => {
        console.error('Error resolving alert:', error);
      }
    });
  }

  getSeverityClass(severity: string): string {
    const classes: { [key: string]: string } = {
      'RED': 'severity-red',
      'ORANGE': 'severity-orange',
      'YELLOW': 'severity-yellow',
      'GREEN': 'severity-green'
    };
    return classes[severity] || 'severity-default';
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  goBack(): void {
    this.location.back();
  }
}

