import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Test, TestSubmission } from '../models/test.model';
import { TestResult } from '../models/result.model';

@Injectable({
  providedIn: 'root'
})
export class TestService {
  private apiUrl = 'http://localhost:8080/api/test';

  constructor(private http: HttpClient) { }

  // Get all tests
  getAllTests(): Observable<Test[]> {
    return this.http.get<Test[]>(this.apiUrl);
  }

  // Get test by ID
  getTestById(id: number): Observable<Test> {
    return this.http.get<Test>(`${this.apiUrl}/${id}`);
  }

  // Create test (Admin only)
  createTest(test: Test): Observable<Test> {
    return this.http.post<Test>(this.apiUrl, test);
  }

  // Delete test (Admin only)
  deleteTest(testId: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${testId}`);
  }

  // Submit test answers
  submitTest(testId: number, answers: TestSubmission): Observable<TestResult> {
    return this.http.post<TestResult>(`${this.apiUrl}/${testId}/submit`, answers);
  }

  // Get all results for a test (Admin only)
  getTestResults(testId: number): Observable<TestResult[]> {
    return this.http.get<TestResult[]>(`${this.apiUrl}/results/${testId}`);
  }

  // Get student results
  getStudentResults(studentId: number): Observable<TestResult[]> {
    return this.http.get<TestResult[]>(`${this.apiUrl}/results/student/${studentId}`);
  }
}
