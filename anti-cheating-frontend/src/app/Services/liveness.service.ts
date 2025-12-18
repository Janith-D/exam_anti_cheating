import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, map } from 'rxjs/operators';

export interface LivenessCheckResult {
  liveness_passed: boolean;
  details: {
    liveness_passed: boolean;
    checks: {
      blink_detection?: {
        passed: boolean;
        blink_count: number;
        score: number;
      };
      head_movement?: {
        passed: boolean;
        details: any;
        score: number;
      };
      texture_analysis?: {
        passed: boolean;
        details: any;
        score: number;
      };
    };
    overall_score: number;
  };
  timestamp: string;
}

export interface VerifyWithLivenessResult {
  studentId: string;
  verification_result: boolean;
  attempts: Array<{
    attempt_number: number;
    success: boolean;
    similarity: number;
    message: string;
    liveness_check?: any;
  }>;
  liveness_check?: any;
  final_message: string;
  timestamp: string;
}

@Injectable({
  providedIn: 'root'
})
export class LivenessService {
  private mlApiUrl = 'http://localhost:5000';

  constructor(private http: HttpClient) {}

  /**
   * Perform standalone liveness check on captured frames
   * @param frames Array of base64 encoded images
   * @returns Observable of liveness check result
   */
  checkLiveness(frames: string[]): Observable<LivenessCheckResult> {
    const url = `${this.mlApiUrl}/liveness-check`;
    
    return this.http.post<LivenessCheckResult>(url, { frames }).pipe(
      map(response => {
        console.log('✅ Liveness check result:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Liveness check error:', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Verify student identity with liveness detection
   * @param studentId Student ID to verify
   * @param frames Array of base64 encoded images
   * @returns Observable of verification result
   */
  verifyWithLiveness(studentId: string, frames: string[]): Observable<VerifyWithLivenessResult> {
    const url = `${this.mlApiUrl}/verify-with-liveness`;
    
    return this.http.post<VerifyWithLivenessResult>(
      url,
      { studentId, frames }
    ).pipe(
      map(response => {
        console.log('✅ Verification with liveness result:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Verification with liveness error:', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Enroll face with ML service
   * @param studentId Student ID
   * @param imageBase64 Base64 encoded image
   * @returns Observable of enrollment result
   */
  enrollFace(studentId: string, imageBase64: string): Observable<any> {
    const url = `${this.mlApiUrl}/enroll`;
    
    return this.http.post(url, {
      studentId,
      image: imageBase64
    }).pipe(
      map(response => {
        console.log('✅ Face enrollment successful:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ Face enrollment error:', error);
        return throwError(() => error);
      })
    );
  }

  /**
   * Check if ML service is available
   * @returns Observable of health status
   */
  checkHealth(): Observable<any> {
    const url = `${this.mlApiUrl}/health`;
    
    return this.http.get(url).pipe(
      map(response => {
        console.log('✅ ML service health:', response);
        return response;
      }),
      catchError(error => {
        console.error('❌ ML service not available:', error);
        return throwError(() => error);
      })
    );
  }
}
