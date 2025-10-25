import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable, tap } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest, Student } from '../models/student.model';

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private apiUrl = 'http://localhost:8080/api/auth';
  private currentUserSubject: BehaviorSubject<Student | null>;
  public currentUser: Observable<Student | null>;

  constructor(private http: HttpClient) {
    const storedUser = localStorage.getItem('currentUser');
    this.currentUserSubject = new BehaviorSubject<Student | null>(
      storedUser ? JSON.parse(storedUser) : null
    );
    this.currentUser = this.currentUserSubject.asObservable();
  }

  public get currentUserValue(): Student | null {
    return this.currentUserSubject.value;
  }

  public get isLoggedIn(): boolean {
    return !!this.getToken();
  }

  public get isAdmin(): boolean {
    const user = this.currentUserValue;
    return user?.role === 'ADMIN';
  }

  public get isStudent(): boolean {
    const user = this.currentUserValue;
    return user?.role === 'STUDENT';
  }

  register(data: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/register`, data).pipe(
      tap(response => {
        this.setAuth(response);
      })
    );
  }

  login(data: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, data).pipe(
      tap(response => {
        this.setAuth(response);
      })
    );
  }

  loginWithFace(formData: FormData): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.apiUrl}/login`, formData).pipe(
      tap(response => {
        this.setAuth(response);
      })
    );
  }

  logout(): void {
    localStorage.removeItem('token');
    localStorage.removeItem('currentUser');
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    return localStorage.getItem('token');
  }

  private setAuth(response: AuthResponse): void {
    console.log('🔐 AuthService.setAuth() called');
    console.log('  Backend response:', response);
    
    // Store token
    localStorage.setItem('token', response.token);
    console.log('  ✅ Token stored in localStorage');
    console.log('  Token preview:', response.token.substring(0, 30) + '...');
    
    // Handle both 'role' (string) and 'roles' (array) from backend
    let userRole: 'ADMIN' | 'STUDENT' = 'STUDENT';
    
    if (response.roles && Array.isArray(response.roles)) {
      // If roles is an array
      userRole = response.roles.some(r => r.includes('ADMIN')) ? 'ADMIN' : 'STUDENT';
    } else if (response.role && typeof response.role === 'string') {
      // If role is a string
      userRole = response.role.includes('ADMIN') ? 'ADMIN' : 'STUDENT';
    }
    
    const user: Student = {
      id: response.userId,
      username: response.username || response.userName || 'unknown', // Handle both username and userName
      email: response.email || '',
      role: userRole
    };
    
    console.log('  Parsed user:', user);
    localStorage.setItem('currentUser', JSON.stringify(user));
    console.log('  ✅ User stored in localStorage');
    this.currentUserSubject.next(user);
    console.log('  ✅ User broadcast to subscribers');
    
    // Verify storage
    const storedToken = localStorage.getItem('token');
    const storedUser = localStorage.getItem('currentUser');
    console.log('  Verification:');
    console.log('    Token in storage:', storedToken ? '✅ Yes' : '❌ No');
    console.log('    User in storage:', storedUser ? '✅ Yes' : '❌ No');
  }
}
