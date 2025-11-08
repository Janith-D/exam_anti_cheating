import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Student } from '../models/student.model';

export interface StudentProfile {
  id: number;
  username: string;
  email: string;
  firstName?: string;
  lastName?: string;
  studentId?: string;
  role: string;
  isActive: boolean;
  createdAt: string;
  updatedAt: string;
}

export interface StudentStatistics {
  studentId: number;
  studentName: string;
  totalExams: number;
  completedExams: number;
  activeExams: number;
  totalAlerts: number;
  criticalAlerts: number;
  highAlerts: number;
  mediumAlerts: number;
  lowAlerts: number;
  currentlyBlocked: boolean;
  blockHistory: BlockInfo[];
  averageScore?: number;
  lastActive?: string;
}

export interface BlockInfo {
  examId: number;
  examTitle?: string;
  blockedAt: string;
  blockedBy: string;
  blockReason: string;
  unblockedAt?: string;
  unblockedBy?: string;
}

export interface StudentAlert {
  id: number;
  severity: string;
  type: string;
  message: string;
  timestamp: string;
  status: string;
  resolvedAt?: string;
  resolvedBy?: string;
  examSession?: {
    id: number;
    exam?: {
      id: number;
      title: string;
    };
  };
}

@Injectable({
  providedIn: 'root'
})
export class StudentService {
  private apiUrl = 'http://localhost:8080/api/students';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${token}`
    });
  }

  private handleError(error: HttpErrorResponse): Observable<never> {
    let errorMessage = 'An error occurred';
    if (error.error instanceof ErrorEvent) {
      errorMessage = `Error: ${error.error.message}`;
    } else {
      errorMessage = `Error Code: ${error.status}\nMessage: ${error.message}`;
    }
    console.error(errorMessage);
    return throwError(() => new Error(errorMessage));
  }

  // Get all students (Admin only)
  getAllStudents(): Observable<StudentProfile[]> {
    return this.http.get<StudentProfile[]>(
      this.apiUrl,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get student by ID
  getStudentById(id: number): Observable<StudentProfile> {
    return this.http.get<StudentProfile>(
      `${this.apiUrl}/${id}`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get student statistics (Admin only)
  getStudentStatistics(id: number): Observable<StudentStatistics> {
    return this.http.get<StudentStatistics>(
      `${this.apiUrl}/${id}/statistics`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get student alerts
  getStudentAlerts(id: number): Observable<StudentAlert[]> {
    return this.http.get<StudentAlert[]>(
      `${this.apiUrl}/${id}/alerts`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }

  // Get student enrollments with block status
  getStudentEnrollments(id: number): Observable<any[]> {
    return this.http.get<any[]>(
      `${this.apiUrl}/${id}/enrollments`,
      { headers: this.getHeaders() }
    ).pipe(catchError(this.handleError));
  }
}
