import { Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { ExamSession } from '../models/exam-session.model';

@Injectable({
  providedIn: 'root'
})
export class ExamSessionService {
  private apiUrl = 'http://localhost:8080/api/sessions';

  constructor(private http: HttpClient) { }

  private handleError(error: HttpErrorResponse): Observable<never> {
    console.error('ExamSession Service Error:', error);
    
    let errorMessage = 'An error occurred';
    
    if (error.status === 401) {
      errorMessage = 'Unauthorized. Please login again.';
      console.error('❌ 401 Unauthorized - Token may be expired or invalid');
    } else if (error.status === 403) {
      errorMessage = 'You do not have permission to perform this action.';
    } else if (error.status === 404) {
      errorMessage = 'Resource not found.';
    } else if (error.error?.message) {
      errorMessage = error.error.message;
    } else if (error.message) {
      errorMessage = error.message;
    }
    
    return throwError(() => new Error(errorMessage));
  }

  // Get all exam sessions
  getAllExamSessions(): Observable<ExamSession[]> {
    return this.http.get<ExamSession[]>(this.apiUrl)
      .pipe(catchError(this.handleError));
  }

  // Get exam session by ID
  getExamSessionById(id: number): Observable<ExamSession> {
    return this.http.get<ExamSession>(`${this.apiUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  // Create exam session (Admin only)
  createExamSession(examSession: ExamSession): Observable<ExamSession> {
    return this.http.post<ExamSession>(this.apiUrl, examSession)
      .pipe(catchError(this.handleError));
  }

  // Update exam session (Admin only)
  updateExamSession(id: number, examSession: ExamSession): Observable<ExamSession> {
    return this.http.put<ExamSession>(`${this.apiUrl}/${id}`, examSession)
      .pipe(catchError(this.handleError));
  }

  // Delete exam session (Admin only)
  deleteExamSession(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`)
      .pipe(catchError(this.handleError));
  }

  // Start exam session (Admin only)
  startExamSession(id: number): Observable<ExamSession> {
    console.log('🚀 Starting exam session:', id);
    return this.http.put<ExamSession>(`${this.apiUrl}/${id}/start`, {})
      .pipe(catchError(this.handleError));
  }

  // End exam session (Admin only)
  endExamSession(id: number): Observable<ExamSession> {
    console.log('🛑 Ending exam session:', id);
    return this.http.put<ExamSession>(`${this.apiUrl}/${id}/end`, {})
      .pipe(catchError(this.handleError));
  }

  // Get active exam sessions
  getActiveExamSessions(): Observable<ExamSession[]> {
    return this.http.get<ExamSession[]>(`${this.apiUrl}/active`);
  }

  // Get or create session for a test (when student starts test)
  getOrCreateSessionForTest(testId: number): Observable<ExamSession> {
    return this.http.post<ExamSession>(`${this.apiUrl}/test/${testId}`, {});
  }
}
