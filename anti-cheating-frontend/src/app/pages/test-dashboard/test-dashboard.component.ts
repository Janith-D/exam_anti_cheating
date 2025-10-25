import { Component, OnInit } from '@angular/core';
import { CommonModule, Location } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../Services/auth.service.service';
import { TestService } from '../../Services/test.service.service';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { Test } from '../../models/test.model';
import { TestResult } from '../../models/result.model';

@Component({
  selector: 'app-test-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './test-dashboard.component.html',
  styleUrl: './test-dashboard.component.css'
})
export class TestDashboardComponent implements OnInit {
  tests: Test[] = [];
  availableExams: any[] = [];
  enrolledExams: any[] = [];
  notEnrolledExams: any[] = [];
  recentResults: TestResult[] = [];
  loading = false;
  errorMessage = '';
  successMessage = '';
  currentUser: any = null;
  studentId: number | null = null;

  constructor(
    private authService: AuthService,
    private testService: TestService,
    private examService: ExamService,
    private enrollmentService: EnrollmentService,
    private router: Router,
    private location: Location
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    this.studentId = this.currentUser?.id || null;
    
    if (this.studentId) {
      this.loadAvailableExams();
      this.loadRecentResults();
    } else {
      this.errorMessage = 'Student ID not found. Please login again.';
    }
    
    this.loadTests();
  }

  loadAvailableExams(): void {
    if (!this.studentId) return;
    
    this.loading = true;
    this.errorMessage = '';
    
    this.examService.getAvailableExamsForStudent(this.studentId).subscribe({
      next: (exams) => {
        this.availableExams = exams;
        
        // Separate enrolled and not enrolled exams
        this.enrolledExams = exams.filter((e: any) => e.isEnrolled);
        this.notEnrolledExams = exams.filter((e: any) => !e.isEnrolled);
        
        console.log('Available exams loaded:', exams);
        console.log('Enrolled exams:', this.enrolledExams);
        console.log('Not enrolled exams:', this.notEnrolledExams);
        
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading available exams:', error);
        this.errorMessage = 'Failed to load available exams. ' + (error.error?.error || error.message);
        this.loading = false;
      }
    });
  }

  loadRecentResults(): void {
    if (!this.studentId) return;
    
    this.testService.getStudentResults(this.studentId).subscribe({
      next: (results) => {
        // Sort by completion date (most recent first) and take top 5
        this.recentResults = results
          .sort((a, b) => {
            const dateA = new Date(a.completedAt || 0).getTime();
            const dateB = new Date(b.completedAt || 0).getTime();
            return dateB - dateA;
          })
          .slice(0, 5);
        
        console.log('Recent results loaded:', this.recentResults);
      },
      error: (error) => {
        console.error('Error loading recent results:', error);
      }
    });
  }

  loadTests(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.testService.getAllTests().subscribe({
      next: (tests) => {
        this.tests = tests;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading tests:', error);
        this.errorMessage = 'Failed to load tests. Please try again.';
        this.loading = false;
      }
    });
  }

  startTest(testId: number): void {
    this.router.navigate(['/test', testId]);
  }

  startExam(examId: number, sessionId: number): void {
    console.log('Starting exam:', examId, 'Session:', sessionId);
    // TODO: Navigate to exam taking interface
    this.router.navigate(['/exam', examId, 'session', sessionId]);
  }

  enrollInExam(examId: number): void {
    if (!this.studentId) {
      this.errorMessage = 'Student ID not found. Please login again.';
      return;
    }

    console.log('Enrolling in exam:', examId);
    
    // For now, show a message. You can implement enrollment with face capture
    this.successMessage = 'Enrollment feature will capture your face for verification.';
    
    // Navigate to enrollment page or show enrollment modal
    this.router.navigate(['/enrollment', examId]);
  }

  canStartExam(exam: any): boolean {
    if (!exam.isEnrolled) return false;
    if (exam.enrollmentStatus !== 'APPROVED') return false;
    if (!exam.activeSessions || exam.activeSessions.length === 0) return false;
    
    return exam.activeSessions.some((s: any) => s.status === 'ACTIVE');
  }

  getActiveSession(exam: any): any {
    if (!exam.activeSessions) return null;
    return exam.activeSessions.find((s: any) => s.status === 'ACTIVE');
  }

  getEnrollmentStatusClass(status: string): string {
    const statusMap: { [key: string]: string } = {
      'APPROVED': 'status-approved',
      'PENDING': 'status-pending',
      'VERIFIED': 'status-verified',
      'REJECTED': 'status-rejected',
      'NOT_ENROLLED': 'status-not-enrolled'
    };
    return statusMap[status] || 'status-unknown';
  }

  getEnrollmentStatusIcon(status: string): string {
    const iconMap: { [key: string]: string } = {
      'APPROVED': '✅',
      'PENDING': '⏳',
      'VERIFIED': '✓',
      'REJECTED': '❌',
      'NOT_ENROLLED': '📝'
    };
    return iconMap[status] || '❓';
  }

  getScoreClass(scorePercentage: number): string {
    if (scorePercentage >= 80) return 'score-excellent';
    if (scorePercentage >= 60) return 'score-good';
    if (scorePercentage >= 40) return 'score-average';
    return 'score-poor';
  }

  viewTestResult(testId: number): void {
    this.router.navigate(['/results'], { queryParams: { testId } });
  }

  retakeTest(testId: number): void {
    this.router.navigate(['/test', testId]);
  }

  viewResults(): void {
    // If there are recent results, scroll to that section
    if (this.recentResults.length > 0) {
      // Scroll to results section smoothly
      setTimeout(() => {
        const resultsSection = document.getElementById('results-section');
        if (resultsSection) {
          resultsSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
      }, 100);
    } else {
      // Show a message if no results yet
      alert('You have not completed any tests yet. Start a test to see your results here!');
    }
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  goBack(): void {
    // Navigate to exam dashboard
    this.router.navigate(['/exam-dashboard']);
  }
}
