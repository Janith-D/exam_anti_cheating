import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { AuthService } from '../../Services/auth.service.service';
import { Exam, ExamStatus, Enrollment, EnrollmentStatus } from '../../models/exam.model';

@Component({
  selector: 'app-admin-exam-management',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './admin-exam-management.component.html',
  styleUrl: './admin-exam-management.component.css'
})
export class AdminExamManagementComponent implements OnInit {
  exams: Exam[] = [];
  loading = false;
  errorMessage = '';
  successMessage = '';
  
  // Create/Edit Exam Modal
  showExamModal = false;
  editingExam: Exam | null = null;
  examForm: Exam = this.getEmptyExam();
  
  // Enrollment Management Modal
  showEnrollmentModal = false;
  selectedExam: Exam | null = null;
  enrollments: Enrollment[] = [];
  
  ExamStatus = ExamStatus;
  EnrollmentStatus = EnrollmentStatus;

  constructor(
    private examService: ExamService,
    private enrollmentService: EnrollmentService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAdmin) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadExams();
  }

  getEmptyExam(): Exam {
    return {
      title: '',
      description: '',
      startDate: '',
      endDate: '',
      status: ExamStatus.DRAFT,
      maxStudents: undefined,
      passingScore: 70
    };
  }

  loadExams(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.examService.getAllExams().subscribe({
      next: (exams) => {
        this.exams = exams;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading exams:', error);
        this.errorMessage = 'Failed to load exams';
        this.loading = false;
      }
    });
  }

  openCreateModal(): void {
    this.editingExam = null;
    this.examForm = this.getEmptyExam();
    this.showExamModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  openEditModal(exam: Exam): void {
    this.editingExam = exam;
    this.examForm = { ...exam };
    this.showExamModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeExamModal(): void {
    this.showExamModal = false;
    this.editingExam = null;
    this.examForm = this.getEmptyExam();
  }

  saveExam(): void {
    if (!this.validateExam()) return;

    this.loading = true;
    
    if (this.editingExam) {
      // Update existing exam
      this.examService.updateExam(this.editingExam.id!, this.examForm).subscribe({
        next: (exam) => {
          this.successMessage = 'Exam updated successfully!';
          this.loadExams();
          setTimeout(() => this.closeExamModal(), 1500);
        },
        error: (error) => {
          console.error('Error updating exam:', error);
          this.errorMessage = 'Failed to update exam';
          this.loading = false;
        }
      });
    } else {
      // Create new exam
      this.examService.createExam(this.examForm).subscribe({
        next: (exam) => {
          this.successMessage = '✅ Exam created successfully! IMPORTANT: Click the 📤 Publish button to make it visible to students.';
          this.loadExams();
          setTimeout(() => {
            this.closeExamModal();
            // Keep message visible longer so admin sees it
          }, 2000);
        },
        error: (error) => {
          console.error('Error creating exam:', error);
          this.errorMessage = 'Failed to create exam';
          this.loading = false;
        }
      });
    }
  }

  validateExam(): boolean {
    if (!this.examForm.title || !this.examForm.description) {
      this.errorMessage = 'Title and description are required';
      return false;
    }
    
    if (!this.examForm.startDate || !this.examForm.endDate) {
      this.errorMessage = 'Start and end dates are required';
      return false;
    }
    
    if (new Date(this.examForm.startDate) >= new Date(this.examForm.endDate)) {
      this.errorMessage = 'End date must be after start date';
      return false;
    }
    
    return true;
  }

  deleteExam(exam: Exam): void {
    if (!confirm(`Are you sure you want to delete "${exam.title}"?`)) {
      return;
    }

    this.examService.deleteExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam deleted successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error deleting exam:', error);
        this.errorMessage = 'Failed to delete exam';
      }
    });
  }

  publishExam(exam: Exam): void {
    if (!confirm(`Publish "${exam.title}"?\n\nThis will make the exam visible to students for enrollment.`)) {
      return;
    }
    
    this.examService.publishExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = `✅ SUCCESS! Exam "${exam.title}" is now PUBLISHED and visible to students. They can now enroll!`;
        this.loadExams();
        setTimeout(() => this.successMessage = '', 6000);
      },
      error: (error) => {
        console.error('Error publishing exam:', error);
        this.errorMessage = 'Failed to publish exam';
      }
    });
  }

  startExam(exam: Exam): void {
    this.examService.startExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam started successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error starting exam:', error);
        this.errorMessage = 'Failed to start exam';
      }
    });
  }

  completeExam(exam: Exam): void {
    if (!confirm(`Are you sure you want to complete "${exam.title}"?`)) {
      return;
    }

    this.examService.completeExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam completed successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error completing exam:', error);
        this.errorMessage = 'Failed to complete exam';
      }
    });
  }

  archiveExam(exam: Exam): void {
    this.examService.archiveExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam archived successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error archiving exam:', error);
        this.errorMessage = 'Failed to archive exam';
      }
    });
  }

  openEnrollmentModal(exam: Exam): void {
    this.selectedExam = exam;
    this.showEnrollmentModal = true;
    this.loadEnrollments(exam.id!);
  }

  closeEnrollmentModal(): void {
    this.showEnrollmentModal = false;
    this.selectedExam = null;
    this.enrollments = [];
  }

  loadEnrollments(examId: number): void {
    this.enrollmentService.getExamEnrollments(examId).subscribe({
      next: (enrollments) => {
        this.enrollments = enrollments;
      },
      error: (error) => {
        console.error('Error loading enrollments:', error);
        this.errorMessage = 'Failed to load enrollments';
      }
    });
  }

  updateEnrollmentStatus(enrollment: Enrollment, status: EnrollmentStatus): void {
    this.enrollmentService.updateEnrollmentStatus(enrollment.id!, status).subscribe({
      next: () => {
        this.successMessage = `Enrollment ${status.toLowerCase()} successfully!`;
        if (this.selectedExam) {
          this.loadEnrollments(this.selectedExam.id!);
        }
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error updating enrollment:', error);
        this.errorMessage = 'Failed to update enrollment status';
      }
    });
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
}
