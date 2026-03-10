import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { DesktopMonitorService, Screenshot, DesktopActivity } from '../../Services/desktop-monitor.service';
import { ExamSessionService } from '../../Services/exam-session.service';
import { StudentService } from '../../Services/student.service';

@Component({
  selector: 'app-screenshot-viewer',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './screenshot-viewer.component.html',
  styleUrls: ['./screenshot-viewer.component.css']
})
export class ScreenshotViewerComponent implements OnInit {
  screenshots: Screenshot[] = [];
  activities: DesktopActivity[] = [];
  loading = false;
  errorMessage = '';
  
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
    private studentService: StudentService
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
    });
  }

  loadFilterOptions(): void {
    // Load exam sessions
    this.examSessionService.getAllSessions().subscribe({
      next: (sessions) => {
        this.sessions = sessions;
      },
      error: (error) => {
        console.error('Error loading sessions:', error);
      }
    });

    // Load students
    this.studentService.getAllStudents().subscribe({
      next: (students) => {
        this.students = students;
      },
      error: (error) => {
        console.error('Error loading students:', error);
      }
    });
  }

  loadScreenshots(): void {
    this.loading = true;
    this.errorMessage = '';

    switch (this.filterMode) {
      case 'session':
        if (this.selectedSessionId) {
          this.loadScreenshotsBySession(this.selectedSessionId);
        }
        break;
      
      case 'student':
        if (this.selectedStudentId) {
          this.loadScreenshotsByStudent(this.selectedStudentId);
        }
        break;
      
      case 'flagged':
        this.loadFlaggedScreenshots();
        break;
      
      case 'all':
      default:
        // Load flagged by default for 'all' mode
        this.loadFlaggedScreenshots();
        break;
    }

    // Also load activities
    this.loadActivities();
  }

  loadScreenshotsBySession(sessionId: number): void {
    this.desktopMonitorService.getScreenshotsBySession(sessionId).subscribe({
      next: (screenshots) => {
        this.screenshots = screenshots;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading screenshots:', error);
        this.errorMessage = 'Failed to load screenshots';
        this.loading = false;
      }
    });
  }

  loadScreenshotsByStudent(studentId: number): void {
    if (this.selectedSessionId) {
      // Load for specific student and session
      this.desktopMonitorService.getScreenshotsByStudentAndSession(studentId, this.selectedSessionId).subscribe({
        next: (screenshots) => {
          this.screenshots = screenshots;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading screenshots:', error);
          this.errorMessage = 'Failed to load screenshots';
          this.loading = false;
        }
      });
    } else {
      // Load all screenshots for student
      this.desktopMonitorService.getScreenshotsByStudent(studentId).subscribe({
        next: (screenshots) => {
          this.screenshots = screenshots;
          this.loading = false;
        },
        error: (error) => {
          console.error('Error loading screenshots:', error);
          this.errorMessage = 'Failed to load screenshots';
          this.loading = false;
        }
      });
    }
  }

  loadFlaggedScreenshots(): void {
    this.desktopMonitorService.getFlaggedScreenshots().subscribe({
      next: (screenshots) => {
        this.screenshots = screenshots;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading flagged screenshots:', error);
        this.errorMessage = 'Failed to load flagged screenshots';
        this.loading = false;
      }
    });
  }

  loadActivities(): void {
    if (this.selectedSessionId) {
      this.desktopMonitorService.getActivitiesBySession(this.selectedSessionId).subscribe({
        next: (activities) => {
          this.activities = activities;
        },
        error: (error) => {
          console.error('Error loading activities:', error);
        }
      });
    } else if (this.selectedStudentId) {
      this.desktopMonitorService.getActivitiesByStudent(this.selectedStudentId).subscribe({
        next: (activities) => {
          this.activities = activities;
        },
        error: (error) => {
          console.error('Error loading activities:', error);
        }
      });
    }
  }

  onFilterChange(): void {
    this.currentPage = 1;
    this.loadScreenshots();
  }

  getScreenshotUrl(screenshot: Screenshot): string {
    return this.desktopMonitorService.getScreenshotUrl(screenshot.id);
  }

  openScreenshot(screenshot: Screenshot): void {
    this.selectedScreenshot = screenshot;
  }

  closeScreenshot(): void {
    this.selectedScreenshot = null;
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
    }
  }

  prevPage(): void {
    if (this.currentPage > 1) {
      this.currentPage--;
    }
  }

  formatTimestamp(timestamp: string): string {
    return new Date(timestamp).toLocaleString();
  }
}
