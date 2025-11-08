import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { StudentService, StudentProfile, StudentStatistics, StudentAlert } from '../../Services/student.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { AuthService } from '../../Services/auth.service.service';

@Component({
  selector: 'app-student-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './student-management.component.html',
  styleUrl: './student-management.component.css'
})
export class StudentManagementComponent implements OnInit {
  // View states
  currentView: 'list' | 'profile' = 'list';
  
  // Student data
  students: StudentProfile[] = [];
  filteredStudents: StudentProfile[] = [];
  selectedStudent: StudentProfile | null = null;
  studentStats: StudentStatistics | null = null;
  studentAlerts: StudentAlert[] = [];
  
  // Search and filter
  searchTerm: string = '';
  filterStatus: 'all' | 'active' | 'inactive' = 'all';
  filterBlocked: 'all' | 'blocked' | 'unblocked' = 'all';
  
  // Alert filtering
  alertSeverityFilter: 'all' | 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW' = 'all';
  filteredAlerts: StudentAlert[] = [];
  
  // Loading states
  loading = false;
  loadingStats = false;
  errorMessage = '';
  successMessage = '';

  constructor(
    private studentService: StudentService,
    private enrollmentService: EnrollmentService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAdmin) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadStudents();
  }

  // ==================== Data Loading ====================
  
  loadStudents(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.studentService.getAllStudents().subscribe({
      next: (students) => {
        this.students = students;
        this.applyFilters();
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading students:', error);
        this.errorMessage = 'Failed to load students';
        this.loading = false;
      }
    });
  }

  loadStudentProfile(studentId: number): void {
    this.loadingStats = true;
    this.currentView = 'profile';
    
    // Load student details
    this.studentService.getStudentById(studentId).subscribe({
      next: (student) => {
        this.selectedStudent = student;
      },
      error: (error) => {
        console.error('Error loading student:', error);
        this.errorMessage = 'Failed to load student details';
      }
    });

    // Load student statistics
    this.studentService.getStudentStatistics(studentId).subscribe({
      next: (stats) => {
        this.studentStats = stats;
        this.loadingStats = false;
      },
      error: (error) => {
        console.error('Error loading statistics:', error);
        this.errorMessage = 'Failed to load student statistics';
        this.loadingStats = false;
      }
    });

    // Load student alerts
    this.studentService.getStudentAlerts(studentId).subscribe({
      next: (alerts) => {
        this.studentAlerts = alerts.sort((a, b) => 
          new Date(b.timestamp).getTime() - new Date(a.timestamp).getTime()
        );
        this.applyAlertFilter();
      },
      error: (error) => {
        console.error('Error loading alerts:', error);
      }
    });
  }

  // ==================== Alert Filtering ====================
  
  applyAlertFilter(): void {
    if (this.alertSeverityFilter === 'all') {
      this.filteredAlerts = [...this.studentAlerts];
    } else {
      this.filteredAlerts = this.studentAlerts.filter(
        alert => alert.severity === this.alertSeverityFilter
      );
    }
  }

  filterAlertsBySeverity(severity: 'all' | 'CRITICAL' | 'HIGH' | 'MEDIUM' | 'LOW'): void {
    this.alertSeverityFilter = severity;
    this.applyAlertFilter();
    
    // Scroll to alerts section
    setTimeout(() => {
      const alertSection = document.querySelector('.alert-history');
      if (alertSection) {
        alertSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
      }
    }, 100);
  }

  // ==================== Search and Filter ====================
  
  applyFilters(): void {
    this.filteredStudents = this.students.filter(student => {
      // Search filter
      const matchesSearch = !this.searchTerm || 
        student.username.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        student.email.toLowerCase().includes(this.searchTerm.toLowerCase()) ||
        (student.firstName && student.firstName.toLowerCase().includes(this.searchTerm.toLowerCase())) ||
        (student.lastName && student.lastName.toLowerCase().includes(this.searchTerm.toLowerCase()));

      // Status filter
      const matchesStatus = this.filterStatus === 'all' || 
        (this.filterStatus === 'active' && student.isActive) ||
        (this.filterStatus === 'inactive' && !student.isActive);

      return matchesSearch && matchesStatus;
    });
  }

  onSearchChange(): void {
    this.applyFilters();
  }

  onFilterChange(): void {
    this.applyFilters();
  }

  // ==================== View Navigation ====================
  
  viewStudentProfile(student: StudentProfile): void {
    this.loadStudentProfile(student.id);
  }

  backToList(): void {
    this.currentView = 'list';
    this.selectedStudent = null;
    this.studentStats = null;
    this.studentAlerts = [];
    this.filteredAlerts = [];
    this.alertSeverityFilter = 'all';
    this.loadStudents();
  }

  goToDashboard(): void {
    this.router.navigate(['/admin/dashboard']);
  }

  // ==================== Student Actions ====================
  
  blockStudent(examId: number, examTitle: string): void {
    if (!this.selectedStudent) return;

    const reason = prompt(`Enter reason for blocking ${this.selectedStudent.username} from "${examTitle}":`);
    if (!reason) return;

    this.enrollmentService.blockStudent(this.selectedStudent.id, examId, reason).subscribe({
      next: () => {
        this.successMessage = `Student blocked from ${examTitle}`;
        this.loadStudentProfile(this.selectedStudent!.id);
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error blocking student:', error);
        this.errorMessage = 'Failed to block student';
      }
    });
  }

  unblockStudent(examId: number, examTitle: string): void {
    if (!this.selectedStudent) return;

    if (!confirm(`Are you sure you want to unblock ${this.selectedStudent.username} from "${examTitle}"?`)) {
      return;
    }

    this.enrollmentService.unblockStudent(this.selectedStudent.id, examId).subscribe({
      next: () => {
        this.successMessage = `Student unblocked from ${examTitle}`;
        this.loadStudentProfile(this.selectedStudent!.id);
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error unblocking student:', error);
        this.errorMessage = 'Failed to unblock student';
      }
    });
  }

  // ==================== Helper Methods ====================
  
  getSeverityClass(severity: string): string {
    switch(severity.toUpperCase()) {
      case 'CRITICAL': return 'severity-critical';
      case 'HIGH': return 'severity-high';
      case 'MEDIUM': return 'severity-medium';
      case 'LOW': return 'severity-low';
      default: return 'severity-info';
    }
  }

  getStatusClass(status: string): string {
    switch(status.toUpperCase()) {
      case 'ACTIVE': return 'status-active';
      case 'RESOLVED': return 'status-resolved';
      case 'DISMISSED': return 'status-dismissed';
      default: return 'status-pending';
    }
  }

  formatDate(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleString();
  }

  formatDateShort(dateString: string): string {
    const date = new Date(dateString);
    return date.toLocaleDateString();
  }

  getAlertIcon(type: string): string {
    switch(type.toUpperCase()) {
      case 'FACE_NOT_DETECTED': return '👤';
      case 'MULTIPLE_FACES': return '👥';
      case 'TAB_SWITCH': return '🔄';
      case 'SUSPICIOUS_ACTIVITY': return '⚠️';
      default: return '🚨';
    }
  }
}
