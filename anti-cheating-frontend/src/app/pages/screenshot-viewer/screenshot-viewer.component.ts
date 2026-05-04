import { Component, OnInit, OnDestroy, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DesktopMonitorService, Screenshot, DesktopActivity } from '../../Services/desktop-monitor.service';
import { ExamSessionService } from '../../Services/exam-session.service';
import { StudentService } from '../../Services/student.service';
import { AlertService } from '../../Services/alert.service.service';
import { Alert } from '../../models/alert.model';

@Component({
  selector: 'app-screenshot-viewer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './screenshot-viewer.component.html',
  styleUrls: ['./screenshot-viewer.component.css']
})
export class ScreenshotViewerComponent implements OnInit, OnDestroy {
  screenshots: Screenshot[] = [];
  activities: DesktopActivity[] = [];
  alerts: Alert[] = [];
  loading = false;
  errorMessage = '';  
  lastRefreshedAt: Date | null = null;
  // Image blob URLs cache
  imageUrls: Map<number, string> = new Map();  
  private refreshTimer: any = null;
  // Filter options
  filterMode: 'session' | 'student' | 'flagged' | 'all' = 'all';
  selectedStudentId: number | null = null;
  selectedSessionId: number | null = null;
  
  // Data for dropdowns
  sessions: any[] = [];
  students: any[] = [];
  
  // Selected screenshot for modal
  selectedScreenshot: Screenshot | null = null;
  
  // Pagination
  currentPage = 1;
  itemsPerPage = 12;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private desktopMonitorService: DesktopMonitorService,
    private examSessionService: ExamSessionService,
    private studentService: StudentService,
    private alertService: AlertService,
    private cdr: ChangeDetectorRef
  ) {}

  ngOnInit(): void {
    // Check route params for pre-selected filters
    this.route.params.subscribe(params => {
      if (params['sessionId']) {
        this.selectedSessionId = +params['sessionId'];
        this.filterMode = 'session';
      } else if (params['studentId']) {
        this.selectedStudentId = +params['studentId'];
        this.filterMode = 'student';
      }
      
      // Load initial data
      this.loadFilterOptions();
      this.loadScreenshots();
      this.loadAlerts();
      this.startAutoRefresh();
    });
  }

  private startAutoRefresh(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }

    this.refreshTimer = setInterval(() => {
      if (!this.loading) {
        this.loadScreenshots();
      }
    }, 10000);
  }

  refreshScreenshots(): void {
    this.loadScreenshots();
  }

  loadFilterOptions(): void {
    // Load exam sessions
    this.examSessionService.getAllExamSessions().subscribe({
      next: (sessions: any[]) => {
        this.sessions = sessions;
      },
      error: (error: any) => {
        console.error('Error loading sessions:', error);
      }
    });

    // Load students
    this.studentService.getAllStudents().subscribe({
      next: (students: any[]) => {
        this.students = students;
      },
      error: (error: any) => {
        console.error('Error loading students:', error);
      }
    });
  }

  loadScreenshots(): void {
    this.loading = true;
    this.errorMessage = '';
    this.clearImageCache();

    switch (this.filterMode) {
      case 'session':
        if (this.selectedSessionId) {
          this.loadScreenshotsBySession(this.selectedSessionId);
        } else {
          this.screenshots = [];
          this.loading = false;
        }
        break;

      case 'student':
        if (this.selectedStudentId) {
          this.loadScreenshotsByStudent(this.selectedStudentId);
        } else {
          this.screenshots = [];
          this.loading = false;
        }
        break;

      case 'flagged':
        this.loadFlaggedScreenshots();
        break;

      case 'all':
      default:
        this.loadAllScreenshots();
        break;
    }

    // Also load activities
    this.loadActivities();
    this.loadAlerts();
  }

  private clearImageCache(): void {
    this.imageUrls.forEach(url => {
      if (url && url !== 'loading') {
        URL.revokeObjectURL(url);
      }
    });
    this.imageUrls.clear();
  }

  loadScreenshotsBySession(sessionId: number): void {
    this.desktopMonitorService.getScreenshotsBySession(sessionId).subscribe({
      next: (screenshots: Screenshot[]) => {
        this.screenshots = screenshots;
        this.loading = false;
        this.lastRefreshedAt = new Date();
        this.preloadScreenshotImages(screenshots);
      },
      error: (error: any) => {
        console.error('Error loading screenshots:', error);
        this.errorMessage = 'Failed to load screenshots';
        this.loading = false;
      }
    });
  }

  loadScreenshotsByStudent(studentId: number): void {
    if (this.selectedSessionId) {
      this.desktopMonitorService.getScreenshotsByStudentAndSession(studentId, this.selectedSessionId).subscribe({
        next: (screenshots: Screenshot[]) => {
          this.screenshots = screenshots;
          this.loading = false;
          this.lastRefreshedAt = new Date();
          this.preloadScreenshotImages(screenshots);
        },
        error: (error: any) => {
          console.error('Error loading screenshots:', error);
          this.errorMessage = 'Failed to load screenshots';
          this.loading = false;
        }
      });
    } else {
      this.desktopMonitorService.getScreenshotsByStudent(studentId).subscribe({
        next: (screenshots: Screenshot[]) => {
          this.screenshots = screenshots;
          this.loading = false;
          this.lastRefreshedAt = new Date();
          this.preloadScreenshotImages(screenshots);
        },
        error: (error: any) => {
          console.error('Error loading screenshots:', error);
          this.errorMessage = 'Failed to load screenshots';
          this.loading = false;
        }
      });
    }
  }

  loadAllScreenshots(): void {
    this.desktopMonitorService.getAllScreenshots().subscribe({
      next: (screenshots: Screenshot[]) => {
        this.screenshots = screenshots;
        this.loading = false;
        this.lastRefreshedAt = new Date();
        this.preloadScreenshotImages(screenshots);
      },
      error: (error: any) => {
        console.error('Error loading all screenshots:', error);
        this.errorMessage = 'Failed to load screenshots';
        this.loading = false;
      }
    });
  }

  loadFlaggedScreenshots(): void {
    this.desktopMonitorService.getFlaggedScreenshots().subscribe({
      next: (screenshots: Screenshot[]) => {
        this.screenshots = screenshots;
        this.loading = false;
        this.lastRefreshedAt = new Date();
        this.preloadScreenshotImages(screenshots);
      },
      error: (error: any) => {
        console.error('Error loading flagged screenshots:', error);
        this.errorMessage = 'Failed to load flagged screenshots';
        this.loading = false;
      }
    });
  }

  loadActivities(): void {
    if (this.selectedSessionId) {
      this.desktopMonitorService.getActivitiesBySession(this.selectedSessionId).subscribe({
        next: (activities: DesktopActivity[]) => {
          this.activities = activities;
        },
        error: (error: any) => {
          console.error('Error loading activities:', error);
        }
      });
    } else if (this.selectedStudentId) {
      this.desktopMonitorService.getActivitiesByStudent(this.selectedStudentId).subscribe({
        next: (activities: DesktopActivity[]) => {
          this.activities = activities;
        },
        error: (error: any) => {
          console.error('Error loading activities:', error);
        }
      });
    }
  }

  loadAlerts(): void {
    if (!this.selectedStudentId) {
      this.alerts = [];
      return;
    }

    this.alertService.getAlertsByStudent(this.selectedStudentId).subscribe({
      next: (alerts: Alert[]) => {
        this.alerts = alerts.sort((a, b) => {
          const dateA = new Date(a.timestamp || a.createdAt || '').getTime();
          const dateB = new Date(b.timestamp || b.createdAt || '').getTime();
          return dateB - dateA;
        });
      },
      error: (error: any) => {
        console.error('Error loading student alerts:', error);
        this.alerts = [];
      }
    });
  }

  onFilterChange(): void {
    this.currentPage = 1;
    this.loadScreenshots();
  }

  getScreenshotUrl(screenshot: Screenshot): string {
    return this.imageUrls.get(screenshot.id) || '';
  }

  private preloadScreenshotImages(screenshots: Screenshot[]): void {
    // Only preload current page screenshots
    const start = (this.currentPage - 1) * this.itemsPerPage;
    const end = start + this.itemsPerPage;
    const pageScreenshots = screenshots.slice(start, end);

    for (const screenshot of pageScreenshots) {
      this.loadScreenshotImage(screenshot.id);
    }
  }

  private loadScreenshotImage(screenshotId: number): void {
    // Skip if already loaded or loading
    if (this.imageUrls.has(screenshotId)) return;

    // Mark as loading with a sentinel value
    this.imageUrls.set(screenshotId, 'loading');

    this.desktopMonitorService.getScreenshotBlob(screenshotId).subscribe({
      next: (blob: Blob) => {
        const url = URL.createObjectURL(blob);
        this.imageUrls.set(screenshotId, url);
        // Force Angular to detect the change and re-render
        this.cdr.detectChanges();
      },
      error: (error: any) => {
        console.error('Error loading screenshot image:', screenshotId, error);
        // Remove the sentinel so it can be retried
        this.imageUrls.delete(screenshotId);
      }
    });
  }

  ngOnDestroy(): void {
    if (this.refreshTimer) {
      clearInterval(this.refreshTimer);
    }

    // Clean up blob URLs
    this.clearImageCache();
  }

  openScreenshot(screenshot: Screenshot): void {
    this.selectedScreenshot = screenshot;
  }

  closeScreenshot(): void {
    this.selectedScreenshot = null;
  }

  clearAllScreenshots(): void {
    const confirmed = window.confirm(
      'Delete ALL screenshots? This action cannot be undone.'
    );
    if (!confirmed) return;

    this.desktopMonitorService.clearAllScreenshots().subscribe({
      next: (response) => {
        this.screenshots = [];
        this.selectedScreenshot = null;
        this.activities = [];
        this.alerts = [];
        this.imageUrls.forEach(url => {
          if (url) URL.revokeObjectURL(url);
        });
        this.imageUrls.clear();
        window.alert(`Cleared ${response.deletedCount} screenshots successfully.`);
      },
      error: (error: any) => {
        console.error('Error clearing screenshots:', error);
        this.errorMessage = 'Failed to clear screenshots';
      }
    });
  }

  getSeverityClass(screenshot: Screenshot): string {
    if (screenshot.flaggedSuspicious) {
      return 'severity-high';
    }
    return 'severity-normal';
  }

  getActivitySeverityClass(activity: DesktopActivity): string {
    if (activity.severityLevel >= 4) {
      return 'severity-high';
    } else if (activity.severityLevel >= 2) {
      return 'severity-medium';
    }
    return 'severity-low';
  }

  getPaginatedScreenshots(): Screenshot[] {
    const start = (this.currentPage - 1) * this.itemsPerPage;
    const end = start + this.itemsPerPage;
    return this.screenshots.slice(start, end);
  }

  getTotalPages(): number {
    return Math.ceil(this.screenshots.length / this.itemsPerPage);
  }

  nextPage(): void {
    if (this.currentPage < this.getTotalPages()) {
      this.currentPage++;
      this.preloadScreenshotImages(this.screenshots);
    }
  }

  prevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
      this.preloadScreenshotImages(this.screenshots);
    }
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
  }

  getAlertTimestamp(alert: Alert): string {
    return alert.timestamp || alert.createdAt || '';
  }

  // Helper methods for filtering (replacing pipe functionality)
  getFlaggedScreenshotsCount(): number {
    return this.screenshots.filter(s => s.flaggedSuspicious).length;
  }

  getHighSeverityActivitiesCount(): number {
    return this.activities.filter(a => a.severityLevel >= 4).length;
  }
}
