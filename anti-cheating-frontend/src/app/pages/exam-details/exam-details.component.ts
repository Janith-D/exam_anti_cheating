import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { TestService } from '../../Services/test.service.service';
import { AuthService } from '../../Services/auth.service.service';
import { Exam, ExamStatus, EnrollmentStatus } from '../../models/exam.model';
import { Test } from '../../models/test.model';

@Component({
  selector: 'app-exam-details',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './exam-details.component.html',
  styleUrl: './exam-details.component.css'
})
export class ExamDetailsComponent implements OnInit {
  exam: Exam | null = null;
  tests: Test[] = [];
  enrollmentStatus: EnrollmentStatus | null = null;
  loading = false;
  errorMessage = '';
  currentUser: any = null;

  ExamStatus = ExamStatus;
  EnrollmentStatus = EnrollmentStatus;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private examService: ExamService,
    private enrollmentService: EnrollmentService,
    private testService: TestService,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }

    const examId = Number(this.route.snapshot.paramMap.get('id'));
    if (examId) {
      this.loadExamDetails(examId);
      this.checkEnrollmentStatus(examId);
    }
  }

  loadExamDetails(examId: number): void {
    this.loading = true;
    this.errorMessage = '';

    this.examService.getExamById(examId).subscribe({
      next: (exam) => {
        this.exam = exam;
        this.loadExamTests();
      },
      error: (error) => {
        console.error('Error loading exam:', error);
        this.errorMessage = 'Failed to load exam details';
        this.loading = false;
      }
    });
  }

  loadExamTests(): void {
    if (!this.exam?.id) {
      this.tests = [];
      this.loading = false;
      return;
    }

    // Load tests that belong to this specific exam
    this.testService.getTestsByExam(this.exam.id).subscribe({
      next: (tests: Test[]) => {
        this.tests = tests;
        console.log(`Loaded ${tests.length} test(s) for exam ${this.exam?.id}:`, tests);
        this.loading = false;
      },
      error: (error: any) => {
        console.error('Error loading tests for exam:', error);
        this.tests = [];
        this.loading = false;
      }
    });
  }

  checkEnrollmentStatus(examId: number): void {
    if (!this.currentUser?.userId) return;

    this.enrollmentService.checkEnrollment(this.currentUser.userId, examId).subscribe({
      next: (response) => {
        if (response.isEnrolled) {
          // Get detailed enrollment info
          this.enrollmentService.getStudentEnrollments(this.currentUser.userId).subscribe({
            next: (enrollments) => {
              const enrollment = enrollments.find(e => e.exam?.id === examId);
              this.enrollmentStatus = enrollment?.status || null;
            }
          });
        }
      },
      error: (error) => {
        console.error('Error checking enrollment:', error);
      }
    });
  }

  canAccessTest(): boolean {
    // Allow access if enrollment is VERIFIED or APPROVED
    return this.enrollmentStatus === EnrollmentStatus.VERIFIED || 
           this.enrollmentStatus === EnrollmentStatus.APPROVED;
  }

  startTest(test: Test): void {
    if (!this.canAccessTest()) {
      alert('You must be enrolled and verified to take this test');
      return;
    }
    this.router.navigate(['/test-page', test.id]);
  }

  getStatusColor(status: ExamStatus): string {
    return this.examService.getStatusColor(status);
  }

  getEnrollmentStatusColor(status: EnrollmentStatus): string {
    return this.enrollmentService.getStatusColor(status);
  }

  getEnrollmentStatusText(status: EnrollmentStatus): string {
    return this.enrollmentService.getStatusText(status);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  goBack(): void {
    this.router.navigate(['/exam-dashboard']);
  }
}
