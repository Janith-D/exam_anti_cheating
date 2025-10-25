import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Alert, AlertCreate, AlertSeverity } from '../models/alert.model';

@Injectable({
  providedIn: 'root'
})
export class AlertService {
  private apiUrl = 'http://localhost:8080/api/alerts';

  constructor(private http: HttpClient) { }

  // Create alert
  createAlert(alert: AlertCreate): Observable<Alert> {
    return this.http.post<Alert>(this.apiUrl, alert);
  }

  // Get all alerts
  getAllAlerts(): Observable<Alert[]> {
    return this.http.get<Alert[]>(this.apiUrl);
  }

  // Get active alerts
  getActiveAlerts(): Observable<Alert[]> {
    return this.http.get<Alert[]>(`${this.apiUrl}/active`);
  }

  // Get alerts by student
  getAlertsByStudent(studentId: number): Observable<Alert[]> {
    return this.http.get<Alert[]>(`${this.apiUrl}/student/${studentId}`);
  }

  // Get alerts by severity
  getAlertsBySeverity(severity: AlertSeverity): Observable<Alert[]> {
    return this.http.get<Alert[]>(`${this.apiUrl}/severity/${severity}`);
  }

  // Resolve alert
  resolveAlert(alertId: number): Observable<Alert> {
    return this.http.put<Alert>(`${this.apiUrl}/${alertId}/resolve`, {});
  }
}
