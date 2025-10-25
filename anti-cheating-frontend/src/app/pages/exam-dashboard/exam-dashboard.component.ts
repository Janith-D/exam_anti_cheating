import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { CameraService } from '../../Services/camera.service';
import { AuthService } from '../../Services/auth.service.service';
import { Exam, ExamStatus, Enrollment, EnrollmentStatus } from '../../models/exam.model';

@Component({
  selector: 'app-exam-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './exam-dashboard.component.html',
  styleUrl: './exam-dashboard.component.css'
})
export class ExamDashboardComponent implements OnInit {
  publishedExams: Exam[] = [];
  enrolledExams: Enrollment[] = [];
  loading = false;
  errorMessage = '';
  successMessage = '';
  currentUser: any = null;
  
  // Enrollment modal
  showEnrollmentModal = false;
  selectedExam: Exam | null = null;
  enrolling = false;
  capturedImage: string | null = null;
  videoElement: HTMLVideoElement | null = null;
  stream: MediaStream | null = null;

  // Status enums for template
  ExamStatus = ExamStatus;
  EnrollmentStatus = EnrollmentStatus;

  constructor(
    private examService: ExamService,
    private enrollmentService: EnrollmentService,
    private cameraService: CameraService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    if (!this.currentUser) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadExams();
    this.loadEnrollments();
  }

  loadExams(): void {
    this.loading = true;
    this.errorMessage = '';
    
    // Load both published and ongoing exams (student-accessible endpoints)
    Promise.all([
      this.examService.getPublishedExams().toPromise(),
      this.examService.getOngoingExams().toPromise()
    ]).then(([publishedExams, ongoingExams]) => {
      // Combine and remove duplicates
      const allExams = [...(ongoingExams || []), ...(publishedExams || [])];
      
      // Remove duplicates by ID
      const uniqueExamsMap = new Map();
      allExams.forEach(exam => {
        if (exam && exam.id) {
          uniqueExamsMap.set(exam.id, exam);
        }
      });
      
      this.publishedExams = Array.from(uniqueExamsMap.values());
      
      // Sort: ONGOING exams first (highlighted), then PUBLISHED
      this.publishedExams.sort((a, b) => {
        if (a.status === ExamStatus.ONGOING && b.status !== ExamStatus.ONGOING) return -1;
        if (a.status !== ExamStatus.ONGOING && b.status === ExamStatus.ONGOING) return 1;
        return 0;
      });
      
      console.log('Loaded exams:', this.publishedExams);
      this.loading = false;
    }).catch(error => {
      console.error('Error loading exams:', error);
      this.errorMessage = 'Failed to load available exams. Please try again.';
      this.loading = false;
    });
  }

  loadEnrollments(): void {
    if (!this.currentUser?.userId) return;
    
    this.enrollmentService.getStudentEnrollments(this.currentUser.userId).subscribe({
      next: (enrollments) => {
        this.enrolledExams = enrollments;
      },
      error: (error) => {
        console.error('Error loading enrollments:', error);
      }
    });
  }

  isEnrolled(examId: number): boolean {
    return this.enrolledExams.some(e => e.exam?.id === examId);
  }

  getEnrollmentStatus(examId: number): EnrollmentStatus | null {
    const enrollment = this.enrolledExams.find(e => e.exam?.id === examId);
    return enrollment?.status || null;
  }

  canEnroll(exam: Exam): boolean {
    return !this.isEnrolled(exam.id!) && 
           (exam.status === ExamStatus.PUBLISHED || exam.status === ExamStatus.ONGOING);
  }

  openEnrollmentModal(exam: Exam): void {
    this.selectedExam = exam;
    this.showEnrollmentModal = true;
    this.errorMessage = '';
    this.successMessage = '';
    this.capturedImage = null;
    
    // Start camera
    setTimeout(() => this.startCamera(), 100);
  }

  closeEnrollmentModal(): void {
    this.showEnrollmentModal = false;
    this.selectedExam = null;
    this.stopCamera();
    this.capturedImage = null;
  }

  async startCamera(): Promise<void> {
    try {
      const video = document.getElementById('enrollmentVideo') as HTMLVideoElement;
      if (!video) return;

      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { width: 640, height: 480 }
      });
      
      video.srcObject = this.stream;
      this.videoElement = video;
    } catch (error) {
      console.error('Error accessing camera:', error);
      this.errorMessage = 'Failed to access camera. Please grant camera permissions.';
    }
  }

  stopCamera(): void {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = null;
    }
    if (this.videoElement) {
      this.videoElement.srcObject = null;
    }
  }

  capturePhoto(): void {
    if (!this.videoElement) return;

    const canvas = document.createElement('canvas');
    canvas.width = this.videoElement.videoWidth;
    canvas.height = this.videoElement.videoHeight;
    
    const context = canvas.getContext('2d');
    if (context) {
      context.drawImage(this.videoElement, 0, 0);
      this.capturedImage = canvas.toDataURL('image/jpeg');
    }
  }

  retakePhoto(): void {
    this.capturedImage = null;
  }

  async enrollInExam(): Promise<void> {
    if (!this.selectedExam || !this.capturedImage || !this.currentUser?.userId) {
      this.errorMessage = 'Please capture your photo first';
      return;
    }

    this.enrolling = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      // Convert base64 to File
      const response = await fetch(this.capturedImage);
      const blob = await response.blob();
      const file = new File([blob], 'face.jpg', { type: 'image/jpeg' });

      this.enrollmentService.enrollInExam(
        this.selectedExam.id!,
        this.currentUser.userId,
        file
      ).subscribe({
        next: (response) => {
          this.successMessage = `Successfully enrolled in ${this.selectedExam!.title}!`;
          this.enrolling = false;
          
          // Reload enrollments
          setTimeout(() => {
            this.loadEnrollments();
            this.closeEnrollmentModal();
          }, 2000);
        },
        error: (error) => {
          console.error('Enrollment error:', error);
          this.errorMessage = error.error?.error || 'Failed to enroll. Please try again.';
          this.enrolling = false;
        }
      });
    } catch (error) {
      console.error('Error processing image:', error);
      this.errorMessage = 'Failed to process image';
      this.enrolling = false;
    }
  }

  viewExamDetails(exam: Exam): void {
    this.router.navigate(['/exam-details', exam.id]);
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

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  navigateToTestDashboard(): void {
    this.router.navigate(['/test-dashboard']);
  }
}
