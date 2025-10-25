import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ExamSessionService } from '../../Services/exam-session.service';
import { TestService } from '../../Services/test.service.service';
import { ExamSession } from '../../models/exam-session.model';
import { Test } from '../../models/test.model';

@Component({
  selector: 'app-exam-session-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './exam-session-management.component.html',
  styleUrl: './exam-session-management.component.css'
})
export class ExamSessionManagementComponent implements OnInit {
  // View states
  currentView: 'list' | 'create' | 'details' = 'list';
  
  // Exam sessions
  examSessions: ExamSession[] = [];
  activeExamSessions: ExamSession[] = [];
  selectedSession: ExamSession | null = null;
  
  // Available tests
  availableTests: Test[] = [];
  
  // Session form
  sessionForm: ExamSession = {
    test: undefined,
    startTime: '',
    endTime: ''
  };
  
  selectedTestId: number | null = null;
  
  // Loading and error states
  loading = false;
  error = '';
  success = '';
  
  // Validation
  formErrors: string[] = [];

  constructor(
    private examSessionService: ExamSessionService,
    private testService: TestService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadExamSessions();
    this.loadAvailableTests();
  }

  loadExamSessions(): void {
    this.loading = true;
    this.examSessionService.getAllExamSessions().subscribe({
      next: (sessions) => {
        this.examSessions = sessions || [];
        this.activeExamSessions = sessions.filter(s => !s.endTime) || [];
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading exam sessions:', error);
        this.error = 'Failed to load exam sessions';
        this.loading = false;
      }
    });
  }

  loadAvailableTests(): void {
    this.testService.getAllTests().subscribe({
      next: (tests) => {
        this.availableTests = tests || [];
      },
      error: (error) => {
        console.error('Error loading tests:', error);
      }
    });
  }

  // View navigation
  showCreateSession(): void {
    this.currentView = 'create';
    this.resetForm();
  }

  showSessionList(): void {
    this.currentView = 'list';
    this.selectedSession = null;
    this.loadExamSessions();
  }

  viewSessionDetails(session: ExamSession): void {
    this.selectedSession = session;
    this.currentView = 'details';
  }

  resetForm(): void {
    const now = new Date();
    const startTime = new Date(now.getTime() + 60000); // 1 minute from now
    const endTime = new Date(startTime.getTime() + 3600000); // 1 hour from start
    
    this.sessionForm = {
      test: undefined,
      startTime: this.formatDateTimeLocal(startTime),
      endTime: this.formatDateTimeLocal(endTime)
    };
    this.selectedTestId = null;
    this.formErrors = [];
    this.error = '';
    this.success = '';
  }

  formatDateTimeLocal(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    const hours = String(date.getHours()).padStart(2, '0');
    const minutes = String(date.getMinutes()).padStart(2, '0');
    return `${year}-${month}-${day}T${hours}:${minutes}`;
  }

  // Form validation
  validateSessionForm(): boolean {
    this.formErrors = [];
    
    if (!this.selectedTestId) {
      this.formErrors.push('Please select a test');
    }
    
    if (!this.sessionForm.startTime) {
      this.formErrors.push('Start time is required');
    }
    
    if (!this.sessionForm.endTime) {
      this.formErrors.push('End time is required');
    }
    
    if (this.sessionForm.startTime && this.sessionForm.endTime) {
      const start = new Date(this.sessionForm.startTime);
      const end = new Date(this.sessionForm.endTime);
      
      if (start >= end) {
        this.formErrors.push('End time must be after start time');
      }
      
      const now = new Date();
      if (start < now) {
        this.formErrors.push('Start time cannot be in the past');
      }
    }
    
    return this.formErrors.length === 0;
  }

  // Create exam session
  createSession(): void {
    if (!this.validateSessionForm()) {
      return;
    }
    
    this.loading = true;
    this.error = '';
    
    const sessionData: ExamSession = {
      test: { id: this.selectedTestId! } as Test,
      startTime: this.sessionForm.startTime,
      endTime: this.sessionForm.endTime
    };
    
    this.examSessionService.createExamSession(sessionData).subscribe({
      next: (session) => {
        this.success = 'Exam session created successfully!';
        setTimeout(() => {
          this.showSessionList();
        }, 2000);
      },
      error: (error) => {
        console.error('Error creating exam session:', error);
        this.error = 'Failed to create exam session';
        this.loading = false;
      }
    });
  }

  // Start exam session
  startSession(sessionId: number | undefined): void {
    if (!sessionId) {
      this.error = 'Invalid session ID';
      return;
    }
    
    if (!confirm('Are you sure you want to start this exam session? Students will be able to access the exam.')) {
      return;
    }
    
    this.examSessionService.startExamSession(sessionId).subscribe({
      next: () => {
        this.success = 'Exam session started successfully!';
        this.loadExamSessions();
        setTimeout(() => this.success = '', 3000);
      },
      error: (error) => {
        console.error('Error starting exam session:', error);
        this.error = 'Failed to start exam session';
      }
    });
  }

  // End exam session
  endSession(sessionId: number | undefined): void {
    if (!sessionId) {
      this.error = 'Invalid session ID';
      return;
    }
    
    if (!confirm('Are you sure you want to end this exam session? All active tests will be submitted automatically.')) {
      return;
    }
    
    this.examSessionService.endExamSession(sessionId).subscribe({
      next: () => {
        this.success = 'Exam session ended successfully!';
        this.loadExamSessions();
        setTimeout(() => this.success = '', 3000);
      },
      error: (error) => {
        console.error('Error ending exam session:', error);
        this.error = 'Failed to end exam session';
      }
    });
  }

  // Delete exam session
  deleteSession(sessionId: number | undefined): void {
    if (!sessionId) {
      this.error = 'Invalid session ID';
      return;
    }
    
    if (!confirm('Are you sure you want to delete this exam session? This action cannot be undone.')) {
      return;
    }
    
    this.examSessionService.deleteExamSession(sessionId).subscribe({
      next: () => {
        this.success = 'Exam session deleted successfully';
        this.loadExamSessions();
        setTimeout(() => this.success = '', 3000);
      },
      error: (error: any) => {
        console.error('Error deleting exam session:', error);
        this.error = 'Failed to delete exam session';
      }
    });
  }

  // Helper methods
  getSessionStatus(session: ExamSession): string {
    if (session.endTime) {
      return 'Completed';
    }
    
    const now = new Date();
    const start = new Date(session.startTime);
    const end = session.endTime ? new Date(session.endTime) : null;
    
    if (now < start) {
      return 'Scheduled';
    }
    
    if (end && now > end) {
      return 'Completed';
    }
    
    return 'Active';
  }

  getStatusClass(status: string): string {
    const classes: { [key: string]: string } = {
      'Scheduled': 'status-scheduled',
      'Active': 'status-active',
      'Completed': 'status-completed'
    };
    return classes[status] || '';
  }

  getTestName(session: ExamSession): string {
    return session.test?.title || 'Unknown Test';
  }

  getDuration(session: ExamSession): string {
    if (!session.startTime || !session.endTime) {
      return 'N/A';
    }
    
    const start = new Date(session.startTime);
    const end = new Date(session.endTime);
    const diff = end.getTime() - start.getTime();
    const hours = Math.floor(diff / 3600000);
    const minutes = Math.floor((diff % 3600000) / 60000);
    
    if (hours > 0) {
      return `${hours}h ${minutes}m`;
    }
    return `${minutes}m`;
  }

  getSelectedTest(): any {
    if (!this.selectedTestId) {
      return null;
    }
    return this.availableTests.find(t => t.id === this.selectedTestId);
  }

  // Navigation
  goBack(): void {
    this.router.navigate(['/admin/dashboard']);
  }
}
