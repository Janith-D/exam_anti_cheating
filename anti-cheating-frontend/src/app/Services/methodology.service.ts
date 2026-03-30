import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError } from 'rxjs/operators';
import { Methodology } from '../models/methodology.model';

export interface MethodologyResponse {
  methodology: Methodology;
  methodologyId: number;
  message: string;
}

@Injectable({
  providedIn: 'root'
})
export class MethodologyService {
  private apiUrl = 'http://localhost:8080/api/methodologies';

  constructor(private http: HttpClient) {}

  private getHeaders(): HttpHeaders {
    const token = localStorage.getItem('token');
    return new HttpHeaders({
      'Authorization': `Bearer ${token}`,
      'Content-Type': 'application/json'
    });
  }

  private handleError(error: any): Observable<never> {
    console.error('Methodology service error:', error);
    return throwError(() => error);
  }

  getAllMethodologies(): Observable<Methodology[]> {
    return this.http.get<Methodology[]>(this.apiUrl, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  getMethodologyById(id: number): Observable<Methodology> {
    return this.http.get<Methodology>(`${this.apiUrl}/${id}`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  getActiveMethodologies(): Observable<Methodology[]> {
    return this.http.get<Methodology[]>(`${this.apiUrl}/active`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  createMethodology(methodology: Methodology): Observable<MethodologyResponse> {
    return this.http.post<MethodologyResponse>(this.apiUrl, methodology, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  updateMethodology(id: number, methodology: Methodology): Observable<MethodologyResponse> {
    return this.http.put<MethodologyResponse>(`${this.apiUrl}/${id}`, methodology, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  deleteMethodology(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  activateMethodology(id: number): Observable<MethodologyResponse> {
    return this.http.put<MethodologyResponse>(`${this.apiUrl}/${id}/activate`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }

  deactivateMethodology(id: number): Observable<MethodologyResponse> {
    return this.http.put<MethodologyResponse>(`${this.apiUrl}/${id}/deactivate`, {}, { headers: this.getHeaders() })
      .pipe(catchError(this.handleError));
  }
}
