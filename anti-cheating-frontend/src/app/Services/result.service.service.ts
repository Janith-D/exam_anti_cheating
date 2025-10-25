import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { TestResult } from '../models/result.model';

@Injectable({
  providedIn: 'root'
})
export class ResultService {
  private apiUrl = 'http://localhost:8080/api/test/results';

  constructor(private http: HttpClient) { }

  // Get all results for a test (Admin only)
  getResultsByTest(testId: number): Observable<TestResult[]> {
    return this.http.get<TestResult[]>(`${this.apiUrl}/${testId}`);
  }

  // Get results for a student
  getResultsByStudent(studentId: number): Observable<TestResult[]> {
    return this.http.get<TestResult[]>(`${this.apiUrl}/student/${studentId}`);
  }
}
