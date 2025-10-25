import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Exam, ExamStatus } from '../models/exam.model';

@Injectable({
  providedIn: 'root'
})
export class ExamService {
  private apiUrl = 'http://localhost:8080/api/exams';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  private handleError(error: any): Observable<never> {
    console.error('Exam service error:', error);
    return throwError(() => error);
  }

  // Get all exams (Admin only)
  getAllExams(): Observable<Exam[]> {
    return this.http.get<Exam[]>(this.apiUrl, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Get exam by ID
  getExamById(examId: number): Observable<Exam> {
    return this.http.get<Exam>(`${this.apiUrl}/${examId}`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Get published exams (available for enrollment)
  getPublishedExams(): Observable<Exam[]> {
    return this.http.get<Exam[]>(`${this.apiUrl}/published`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Get ongoing exams
  getOngoingExams(): Observable<Exam[]> {
    return this.http.get<Exam[]>(`${this.apiUrl}/ongoing`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Create exam (Admin only)
  createExam(exam: Exam): Observable<Exam> {
    return this.http.post<Exam>(this.apiUrl, exam, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Update exam (Admin only)
  updateExam(examId: number, exam: Exam): Observable<Exam> {
    return this.http.put<Exam>(`${this.apiUrl}/${examId}`, exam, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Delete exam (Admin only)
  deleteExam(examId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${examId}`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Publish exam (Admin only)
  publishExam(examId: number): Observable<Exam> {
    return this.http.put<Exam>(`${this.apiUrl}/${examId}/publish`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Start exam (Admin only)
  startExam(examId: number): Observable<Exam> {
    return this.http.put<Exam>(`${this.apiUrl}/${examId}/start`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Complete exam (Admin only)
  completeExam(examId: number): Observable<Exam> {
    return this.http.put<Exam>(`${this.apiUrl}/${examId}/complete`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Archive exam (Admin only)
  archiveExam(examId: number): Observable<Exam> {
    return this.http.put<Exam>(`${this.apiUrl}/${examId}/archive`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Get available exams for student (with sessions and enrollment status)
  getAvailableExamsForStudent(studentId: number): Observable<any[]> {
    return this.http.get<any[]>(`${this.apiUrl}/student/${studentId}/available`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  // Get exam status badge color
  getStatusColor(status: ExamStatus): string {
    switch (status) {
      case ExamStatus.DRAFT:
        return 'secondary';
      case ExamStatus.PUBLISHED:
        return 'primary';
      case ExamStatus.ONGOING:
        return 'success';
      case ExamStatus.COMPLETED:
        return 'info';
      case ExamStatus.ARCHIVED:
        return 'dark';
      default:
        return 'secondary';
    }
  }

  // Check if exam is available for enrollment
  isEnrollmentOpen(exam: Exam): boolean {
    return exam.status === ExamStatus.PUBLISHED || exam.status === ExamStatus.ONGOING;
  }

  // Check if exam has started
  hasExamStarted(exam: Exam): boolean {
    if (!exam.startDate) return false;
    return new Date(exam.startDate) <= new Date();
  }

  // Check if exam has ended
  hasExamEnded(exam: Exam): boolean {
    if (!exam.endDate) return false;
    return new Date(exam.endDate) <= new Date();
  }
}
