import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { ExamSessionService } from '../../Services/exam-session.service';
import { TestService } from '../../Services/test.service.service';
import { ExamService } from '../../Services/exam.service';
import { ExamSession } from '../../models/exam-session.model';
import { Test } from '../../models/test.model';
import { Exam, ExamStatus } from '../../models/exam.model';

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
  
  // Available exams (changed from tests to exams)
  availableExams: Exam[] = [];
  availableTests: Test[] = []; // Keep for compatibility
  
  // Session form
  sessionForm: ExamSession = {
    startTime: '',
    endTime: ''
  };
  
  selectedExamId: number | null = null; // Changed from selectedTestId
  selectedTestId: number | null = null; // Keep for compatibility
  
  // Loading and error states
  loading = false;
  error = '';
  success = '';
  
  // Validation
  formErrors: string[] = [];
  
  // Enum for template access
  ExamStatus = ExamStatus;

  constructor(
    private examSessionService: ExamSessionService,
    private testService: TestService,
    private examService: ExamService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadExamSessions();
    this.loadAvailableExams(); // Load exams instead of tests
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

  // NEW: Load available exams
  loadAvailableExams(): void {
    this.examService.getAllExams().subscribe({
      next: (exams) => {
        this.availableExams = exams || [];
        console.log('📚 Loaded exams for session creation:', this.availableExams);
      },
      error: (error) => {
        console.error('Error loading exams:', error);
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
      startTime: this.formatDateTimeLocal(startTime),
      endTime: this.formatDateTimeLocal(endTime)
    };
    this.selectedExamId = null;
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
    
    if (!this.selectedExamId) {
      this.formErrors.push('Please select an exam');
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
    
    const sessionData: any = {
      examId: this.selectedExamId!, // Send examId to backend
      startTime: this.sessionForm.startTime,
      endTime: this.sessionForm.endTime,
      createdBy: 'admin' // TODO: Get from auth service
    };
    
    console.log('Creating session with data:', sessionData);
    
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
    // Try to get name from exam first, then test, then examName field
    return session.exam?.title || session.test?.title || session.examName || 'Unknown Test';
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

  // NEW: Get selected exam
  getSelectedExam(): Exam | undefined {
    if (!this.selectedExamId) {
      return undefined;
    }
    return this.availableExams.find(e => e.id === this.selectedExamId);
  }

  // Get exam from session
  getExamFromSession(session: ExamSession): any {
    // First try direct exam relationship, then fall back to test.exam
    return session.exam || session.test?.exam;
  }

  // Check if exam can be published (is DRAFT)
  canPublishExam(session: ExamSession): boolean {
    const exam = this.getExamFromSession(session);
    return exam && exam.status === ExamStatus.DRAFT;
  }

  // Check if exam is published
  isExamPublished(session: ExamSession): boolean {
    const exam = this.getExamFromSession(session);
    return exam && (exam.status === ExamStatus.PUBLISHED || exam.status === ExamStatus.ONGOING);
  }

  // Publish exam from session
  publishExamFromSession(session: ExamSession): void {
    const exam = this.getExamFromSession(session);
    
    if (!exam || !exam.id) {
      this.error = 'No exam associated with this session';
      setTimeout(() => this.error = '', 3000);
      return;
    }

    if (!confirm(`Publish exam "${exam.title}"?\n\nThis will make the exam visible to students for enrollment.`)) {
      return;
    }

    this.loading = true;
    this.examService.publishExam(exam.id).subscribe({
      next: () => {
        this.success = `✅ SUCCESS! Exam "${exam.title}" is now PUBLISHED and visible to students!`;
        this.loadExamSessions();
        setTimeout(() => {
          this.success = '';
          this.loading = false;
        }, 5000);
      },
      error: (error) => {
        console.error('Error publishing exam:', error);
        this.error = 'Failed to publish exam. Please try again.';
        this.loading = false;
        setTimeout(() => this.error = '', 3000);
      }
    });
  }

  // Navigation
  goBack(): void {
    this.router.navigate(['/admin/dashboard']);
  }
}
