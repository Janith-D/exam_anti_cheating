export interface Student {
  id?: number;
  username: string;
  email: string;
  password?: string;
  role: 'STUDENT' | 'ADMIN';
  createdAt?: string;
}

export interface AuthResponse {
  token: string;
  userId: number;
  username?: string;
  userName?: string; // Backend might send userName instead of username
  email: string;
  roles?: string[]; // May be undefined
  role?: string; // Backend might send single role string
}

export interface LoginRequest {
  username: string;
  password: string;
}

export interface RegisterRequest {
  username: string;
  email: string;
  password: string;
  role: 'STUDENT' | 'ADMIN';
}
