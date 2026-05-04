import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { CameraService } from '../../Services/camera.service';
import { AuthService } from '../../Services/auth.service.service';
import { DesktopMonitorService } from '../../Services/desktop-monitor.service';
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
  currentUser: any = null; // Using any to handle both id and userId properties
  
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
    private desktopMonitorService: DesktopMonitorService,
    public router: Router
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    console.log('📱 Current user from auth service:', this.currentUser);
    
    // Fallback: Try to get user from localStorage if not in auth service
    if (!this.currentUser) {
      const storedUser = localStorage.getItem('currentUser');
      if (storedUser) {
        this.currentUser = JSON.parse(storedUser);
        console.log('📦 User loaded from localStorage:', this.currentUser);
      }
    }
    
    if (!this.currentUser) {
      console.error('❌ No user found, redirecting to login');
      this.router.navigate(['/login']);
      return;
    }
    
    // Ensure user has an ID
    const studentId = this.currentUser?.id || this.currentUser?.userId;
    if (!studentId) {
      console.error('⚠️ User object missing ID:', this.currentUser);
      this.errorMessage = 'Authentication error. Please login again.';
      setTimeout(() => {
        this.authService.logout();
        this.router.navigate(['/login']);
      }, 2000);
      return;
    }
    
    console.log('✅ User authenticated with ID:', studentId);
    this.loadExams();
    this.loadEnrollments();
  }

  loadExams(): void {
    this.loading = true;
    this.errorMessage = '';
    
    // Load both published and ongoing exams, but tolerate a single endpoint failure.
    Promise.allSettled([
      this.examService.getPublishedExams().toPromise(),
      this.examService.getOngoingExams().toPromise()
    ]).then((results) => {
      const successfulExams = results.reduce<Exam[]>((allExams, result) => {
        if (result.status === 'fulfilled' && Array.isArray(result.value)) {
          allExams.push(...result.value);
        }
        return allExams;
      }, []);

      const uniqueExamsMap = new Map<number, Exam>();
      successfulExams.forEach((exam) => {
        if (exam?.id) {
          uniqueExamsMap.set(exam.id, exam);
        }
      });

      this.publishedExams = Array.from(uniqueExamsMap.values()).sort((a, b) => {
        if (a.status === ExamStatus.ONGOING && b.status !== ExamStatus.ONGOING) return -1;
        if (a.status !== ExamStatus.ONGOING && b.status === ExamStatus.ONGOING) return 1;
        return 0;
      });

      const allFailed = results.every((result) => result.status === 'rejected');
      if (allFailed) {
        console.error('Error loading exams:', results);
        this.errorMessage = 'Failed to load available exams. Please try again.';
      }

      this.loading = false;
    });
  }

  loadEnrollments(): void {
    const studentId = this.currentUser?.id || this.currentUser?.userId;
    if (!studentId) {
      console.error('❌ No student ID available for loading enrollments');
      return;
    }
    
    this.enrollmentService.getStudentEnrollments(studentId).subscribe({
      next: (enrollments) => {
        // Filter out enrollments without valid exam data (face-only enrollments)
        this.enrolledExams = enrollments.filter(e => e.exam != null);
        console.log('✅ Loaded enrollments with valid exams:', this.enrolledExams.length);
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
    return exam.status === ExamStatus.PUBLISHED || exam.status === ExamStatus.ONGOING;
  }

  openEnrollmentModal(exam: Exam): void {
    console.log('Opening enrollment modal for:', exam.title);
    this.selectedExam = exam;
    this.showEnrollmentModal = true;
    this.capturedImage = null;
    this.errorMessage = '';
    this.successMessage = '';
    this.startCamera();
  }

  closeEnrollmentModal(): void {
    this.showEnrollmentModal = false;
    this.selectedExam = null;
    this.stopCamera();
    this.capturedImage = null;
  }

  async startCamera(): Promise<void> {
    try {
      console.log('🎥 Starting camera...');
      const video = document.getElementById('enrollmentVideo') as HTMLVideoElement;
      if (!video) {
        console.error('❌ Video element not found');
        this.errorMessage = 'Camera initialization failed. Please try again.';
        return;
      }

      this.stream = await navigator.mediaDevices.getUserMedia({
        video: { 
          width: { ideal: 640 },
          height: { ideal: 480 },
          facingMode: 'user'
        }
      });
      
      video.srcObject = this.stream;
      this.videoElement = video;
      
      // Wait for video to be ready
      await new Promise<void>((resolve) => {
        video.onloadedmetadata = () => {
          console.log('✅ Camera ready');
          resolve();
        };
      });
      
    } catch (error: any) {
      console.error('❌ Error accessing camera:', error);
      if (error.name === 'NotAllowedError') {
        this.errorMessage = 'Camera access denied. Please grant camera permissions and try again.';
      } else if (error.name === 'NotFoundError') {
        this.errorMessage = 'No camera found. Please connect a camera and try again.';
      } else {
        this.errorMessage = 'Failed to access camera. Please check your camera settings.';
      }
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
    console.log('📸 Capturing photo...');
    
    if (!this.videoElement) {
      console.error('❌ Video element not available');
      this.errorMessage = 'Camera not ready. Please wait and try again.';
      return;
    }

    if (this.videoElement.readyState !== this.videoElement.HAVE_ENOUGH_DATA) {
      console.error('❌ Video not ready:', this.videoElement.readyState);
      this.errorMessage = 'Camera is still loading. Please wait a moment and try again.';
      return;
    }

    try {
      const canvas = document.createElement('canvas');
      canvas.width = this.videoElement.videoWidth;
      canvas.height = this.videoElement.videoHeight;
      
      console.log('📐 Canvas size:', canvas.width, 'x', canvas.height);
      
      if (canvas.width === 0 || canvas.height === 0) {
        throw new Error('Invalid video dimensions');
      }
      
      const context = canvas.getContext('2d');
      if (context) {
        context.drawImage(this.videoElement, 0, 0);
        this.capturedImage = canvas.toDataURL('image/jpeg', 0.95);
        console.log('✅ Photo captured successfully');
        console.log('📊 Image size:', Math.round(this.capturedImage.length / 1024), 'KB');
      } else {
        throw new Error('Failed to get canvas context');
      }
    } catch (error) {
      console.error('❌ Error capturing photo:', error);
      this.errorMessage = 'Failed to capture photo. Please try again.';
    }
  }

  retakePhoto(): void {
    console.log('🔄 Retaking photo...');
    this.capturedImage = null;
    this.errorMessage = '';
    this.successMessage = '';
  }

  async enrollInExam(): Promise<void> {
    console.log('🚀 enrollInExam() called');
    console.log('📋 selectedExam:', this.selectedExam);
    console.log('📸 capturedImage exists:', !!this.capturedImage);
    console.log('📸 capturedImage length:', this.capturedImage?.length);
    console.log('👤 currentUser:', this.currentUser);
    
    if (!this.selectedExam) {
      console.error('❌ No exam selected');
      this.errorMessage = 'No exam selected';
      return;
    }
    
    if (!this.capturedImage) {
      console.error('❌ No captured image');
      this.errorMessage = 'Please capture your photo first';
      return;
    }
    
    // Support both id and userId properties
    const studentId = this.currentUser?.id || this.currentUser?.userId;
    if (!studentId) {
      console.error('❌ No user ID available');
      console.error('Current user object:', this.currentUser);
      this.errorMessage = 'User not authenticated. Please login again.';
      return;
    }

    console.log('✅ All validation passed, proceeding with enrollment...');
    console.log('📝 Using student ID:', studentId);
    this.enrolling = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      console.log('🔄 Starting enrollment process...');
      console.log('📝 Exam ID:', this.selectedExam.id);
      console.log('👤 Student ID:', studentId);
      
      // Check enrollment status
      const enrollmentStatus = this.getEnrollmentStatus(this.selectedExam.id!);
      console.log('📊 Current enrollment status:', enrollmentStatus);
      
      // Determine if user is already enrolled
      const isAlreadyEnrolled = enrollmentStatus === EnrollmentStatus.VERIFIED || 
                                enrollmentStatus === EnrollmentStatus.APPROVED;
      
      if (isAlreadyEnrolled) {
        console.log('✅ User already enrolled, using VERIFY endpoint instead of ENROLL');
      } else {
        console.log('⚠️ User not yet enrolled, using ENROLL endpoint');
      }
      
      // Convert base64 to File
      const response = await fetch(this.capturedImage);
      const blob = await response.blob();
      const file = new File([blob], 'face.jpg', { type: 'image/jpeg' });
      console.log('📸 Image file created:', file.size, 'bytes');

      // Call appropriate endpoint based on enrollment status
      const enrollmentRequest = isAlreadyEnrolled
        ? this.enrollmentService.verifyInExam(this.selectedExam.id!, studentId, file)
        : this.enrollmentService.enrollInExam(this.selectedExam.id!, studentId, file);

      enrollmentRequest.subscribe({
        next: (response) => {
          console.log('✅ Enrollment/Verification successful:', response);
          this.successMessage = `Successfully verified for ${this.selectedExam!.title}! Redirecting to exam...`;
          this.enrolling = false;
          
          // Store the exam for navigation
          const enrolledExam = this.selectedExam!;
          
          // Close modal and navigate to exam details
          setTimeout(() => {
            this.closeEnrollmentModal();
            this.loadEnrollments();
            // Navigate to exam details page
            console.log('🎯 Navigating to exam details:', enrolledExam.id);
            this.router.navigate(['/exam-details', enrolledExam.id]);
          }, 1500);
        },
        error: (error) => {
          console.error('❌ Enrollment error:', error);
          console.error('Error status:', error.status);
          console.error('Error body:', error.error);
          
          let errorMsg = 'Failed to enroll. ';
          if (error.error?.error) {
            errorMsg += error.error.error;
          } else if (error.status === 400) {
            errorMsg += 'Face verification failed. Please ensure your face is clearly visible and try again.';
          } else if (error.status === 503) {
            errorMsg += 'Face verification service is unavailable. Please try again later.';
          } else if (error.status === 0) {
            errorMsg += 'Cannot connect to server. Please check your connection.';
          } else {
            errorMsg += 'Please try again.';
          }
          
          this.errorMessage = errorMsg;
          this.enrolling = false;
        }
      });
    } catch (error) {
      console.error('Error processing image:', error);
      this.errorMessage = 'Failed to process image. Please try again.';
      this.enrolling = false;
    }
  }

  takeExam(exam: Exam): void {
    if (!exam || !exam.id) {
      console.error('Cannot take exam: exam is null or missing ID');
      return;
    }
    console.log('📝 Taking exam:', exam.title);
    this.router.navigate(['/exam-details', exam.id]);
  }

  viewExamDetails(exam: Exam | undefined | null): void {
    if (!exam || !exam.id) {
      console.error('Cannot view exam details: exam is null or missing ID');
      return;
    }
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

  getLiveExamCount(): number {
    return this.publishedExams.filter(exam => exam.status === ExamStatus.ONGOING).length;
  }

  getPublishedExamCount(): number {
    return this.publishedExams.filter(exam => exam.status === ExamStatus.PUBLISHED).length;
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

  getUserDisplayName(): string {
    if (this.currentUser?.username) {
      return this.currentUser.username;
    }
    if (this.currentUser?.userName) {
      return this.currentUser.userName;
    }
    const storedUser = localStorage.getItem('currentUser');
    if (storedUser) {
      try {
        const user = JSON.parse(storedUser);
        return user.username || user.userName || 'Student';
      } catch (e) {
        console.error('Error parsing stored user:', e);
      }
    }
    return 'Student';
  }
}
