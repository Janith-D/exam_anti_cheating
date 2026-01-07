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
    const activeSession = localStorage.getItem('activeSession');
    
    // If there's an active session, load that user's data
    let user = storedUser ? JSON.parse(storedUser) : null;
    if (activeSession && user) {
      // Verify the active session matches the stored user
      const sessionToken = localStorage.getItem(`token_${activeSession}`);
      if (!sessionToken) {
        user = null; // Session is invalid
      }
    }
    
    this.currentUserSubject = new BehaviorSubject<Student | null>(user);
    this.currentUser = this.currentUserSubject.asObservable();
  }

  // Generate unique session key based on username
  private getSessionKey(username: string): string {
    return username.toLowerCase();
  }

  // Switch to a different session
  public switchSession(username: string): boolean {
    const sessionKey = this.getSessionKey(username);
    const token = localStorage.getItem(`token_${sessionKey}`);
    const user = localStorage.getItem(`user_${sessionKey}`);
    
    if (token && user) {
      localStorage.setItem('token', token);
      localStorage.setItem('currentUser', user);
      localStorage.setItem('activeSession', sessionKey);
      this.currentUserSubject.next(JSON.parse(user));
      console.log(`✅ Switched to session: ${username}`);
      return true;
    }
    return false;
  }

  // Get all active sessions
  public getActiveSessions(): Array<{username: string, role: string}> {
    const sessions: Array<{username: string, role: string}> = [];
    for (let i = 0; i < localStorage.length; i++) {
      const key = localStorage.key(i);
      if (key?.startsWith('user_')) {
        const userData = localStorage.getItem(key);
        if (userData) {
          const user = JSON.parse(userData);
          sessions.push({ username: user.username, role: user.role });
        }
      }
    }
    return sessions;
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

  logout(logoutAll: boolean = false): void {
    const currentUser = this.currentUserValue;
    
    if (logoutAll) {
      // Logout all sessions
      localStorage.clear();
      console.log('🚪 Logged out all sessions');
    } else if (currentUser) {
      // Logout only current session
      const sessionKey = this.getSessionKey(currentUser.username);
      localStorage.removeItem(`token_${sessionKey}`);
      localStorage.removeItem(`user_${sessionKey}`);
      localStorage.removeItem('token');
      localStorage.removeItem('currentUser');
      localStorage.removeItem('activeSession');
      console.log(`🚪 Logged out session: ${currentUser.username}`);
    } else {
      // Fallback: clear current session data
      localStorage.removeItem('token');
      localStorage.removeItem('currentUser');
      localStorage.removeItem('activeSession');
    }
    
    this.currentUserSubject.next(null);
  }

  getToken(): string | null {
    // First try to get token from current session
    const token = localStorage.getItem('token');
    if (token) return token;
    
    // If no token, try to get from active session
    const activeSession = localStorage.getItem('activeSession');
    if (activeSession) {
      return localStorage.getItem(`token_${activeSession}`);
    }
    
    return null;
  }

  private setAuth(response: AuthResponse): void {
    console.log('🔐 AuthService.setAuth() called');
    console.log('  Backend response:', response);
    
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
      username: response.username || response.userName || 'unknown',
      email: response.email || '',
      role: userRole
    };
    
    const sessionKey = this.getSessionKey(user.username);
    
    // Store session-specific data
    localStorage.setItem(`token_${sessionKey}`, response.token);
    localStorage.setItem(`user_${sessionKey}`, JSON.stringify(user));
    
    // Store as current active session
    localStorage.setItem('token', response.token);
    localStorage.setItem('currentUser', JSON.stringify(user));
    localStorage.setItem('activeSession', sessionKey);
    
    console.log('  ✅ Token stored:', response.token.substring(0, 30) + '...');
    console.log('  ✅ Session key:', sessionKey);
    console.log('  ✅ User:', user.username, '(' + user.role + ')');
    
    this.currentUserSubject.next(user);
    console.log('  ✅ Multi-session login complete');
    
    // Show active sessions
    const sessions = this.getActiveSessions();
    console.log('  📋 Active sessions:', sessions.map(s => `${s.username} (${s.role})`).join(', '));
  }
}
