import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Enrollment, EnrollmentResponse, EnrollmentStatus } from '../models/exam.model';

@Injectable({
  providedIn: 'root'
})
export class EnrollmentService {
  private apiUrl = 'http://localhost:8080/api/enrollment';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`
    });
  }

  private handleError(error: any): Observable<never> {
    console.error('Enrollment service error:', error);
    return throwError(() => error);
  }

  // Enroll in exam with face verification
  enrollInExam(examId: number, studentId: number, image: File): Observable<EnrollmentResponse> {
    const formData = new FormData();
    formData.append('studentId', studentId.toString());
    formData.append('image', image);

    const headers = new HttpHeaders({
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    });

    return this.http.post<EnrollmentResponse>(
      `${this.apiUrl}/exam/${examId}`,
      formData,
      { headers }
    ).pipe(catchError(this.handleError));
  }

  // Get student's enrollments
  getStudentEnrollments(studentId: number): Observable<Enrollment[]> {
    return this.http.get<Enrollment[]>(
      `${this.apiUrl}/student/${studentId}/exams`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get exam enrollments (Admin only)
  getExamEnrollments(examId: number): Observable<Enrollment[]> {
    return this.http.get<Enrollment[]>(
      `${this.apiUrl}/exam/${examId}/students`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get exam enrollments by status (Admin only)
  getExamEnrollmentsByStatus(examId: number, status: EnrollmentStatus): Observable<Enrollment[]> {
    return this.http.get<Enrollment[]>(
      `${this.apiUrl}/exam/${examId}/students/status/${status}`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Update enrollment status (Admin only)
  updateEnrollmentStatus(enrollmentId: number, status: EnrollmentStatus): Observable<any> {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    });

    return this.http.put(
      `${this.apiUrl}/${enrollmentId}/status?status=${status}`,
      {},
      { headers }
    ).pipe(catchError(this.handleError));
  }

  // Check if student is enrolled in exam
  checkEnrollment(studentId: number, examId: number): Observable<{ isEnrolled: boolean }> {
    return this.http.get<{ isEnrolled: boolean }>(
      `${this.apiUrl}/check/${studentId}/exam/${examId}`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get enrollment status badge color
  getStatusColor(status: EnrollmentStatus): string {
    switch (status) {
      case EnrollmentStatus.PENDING:
        return 'warning';
      case EnrollmentStatus.VERIFIED:
        return 'info';
      case EnrollmentStatus.APPROVED:
        return 'success';
      case EnrollmentStatus.REJECTED:
        return 'danger';
      default:
        return 'secondary';
    }
  }

  // Get enrollment status display text
  getStatusText(status: EnrollmentStatus): string {
    switch (status) {
      case EnrollmentStatus.PENDING:
        return 'Pending Review';
      case EnrollmentStatus.VERIFIED:
        return 'Face Verified';
      case EnrollmentStatus.APPROVED:
        return 'Enrolled';
      case EnrollmentStatus.REJECTED:
        return 'Rejected';
      default:
        return status;
    }
  }

  // Block student from exam (Admin only)
  blockStudent(studentId: number, examId: number, reason?: string): Observable<any> {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    });

    const params = reason ? `?reason=${encodeURIComponent(reason)}` : '';
    return this.http.post(
      `${this.apiUrl}/block/${studentId}/exam/${examId}${params}`,
      {},
      { headers }
    ).pipe(catchError(this.handleError));
  }

  // Unblock student from exam (Admin only)
  unblockStudent(studentId: number, examId: number): Observable<any> {
    const headers = new HttpHeaders({
      'Authorization': `Bearer ${localStorage.getItem('token')}`
    });

    return this.http.post(
      `${this.apiUrl}/unblock/${studentId}/exam/${examId}`,
      {},
      { headers }
    ).pipe(catchError(this.handleError));
  }

  // Check if student is blocked in exam
  checkBlockStatus(studentId: number, examId: number): Observable<any> {
    return this.http.get(
      `${this.apiUrl}/check-block/${studentId}/exam/${examId}`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }
}
