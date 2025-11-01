import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Event } from '../models/event.model';

@Injectable({
  providedIn: 'root'
})
export class EventService {
  private apiUrl = 'http://localhost:8080/api/events';

  constructor(private http: HttpClient) {}

  /**
   * Log an event to the backend
   */
  logEvent(event: any): Observable<Event> {
    return this.http.post<Event>(`${this.apiUrl}/log`, event);
  }

  /**
   * Get events for a specific student
   */
  getEventsByStudent(studentId: number): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/student/${studentId}`);
  }

  /**
   * Get events for a specific test
   */
  getEventsByTest(testId: number): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/test/${testId}`);
  }

  /**
   * Get events by type
   */
  getEventsByType(eventType: string): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/type/${eventType}`);
  }

  /**
   * Get events for a specific student and test
   */
  getEventsByStudentAndTest(studentId: number, testId: number): Observable<Event[]> {
    return this.http.get<Event[]>(`${this.apiUrl}/student/${studentId}/test/${testId}`);
  }
  
  /**
   * Get events for a specific exam session
   */
  getEventsBySession(sessionId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/session/${sessionId}`);
  }
  
  /**
   * Get all events for a student (no date filtering)
   */
  getAllEventsByStudent(studentId: number): Observable<any> {
    return this.http.get<any>(`${this.apiUrl}/student/${studentId}/all`);
  }
}
