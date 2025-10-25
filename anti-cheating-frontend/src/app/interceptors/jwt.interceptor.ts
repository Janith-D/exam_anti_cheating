import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../Services/auth.service.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  const token = authService.getToken();
  
  // Debug logging
  const directToken = localStorage.getItem('token');
  console.log('🔒 JWT Interceptor:');
  console.log('  Request URL:', req.url);
  console.log('  Token from AuthService:', token ? `${token.substring(0, 20)}...` : 'NULL');
  console.log('  Token from localStorage:', directToken ? `${directToken.substring(0, 20)}...` : 'NULL');

  if (token) {
    console.log('  ✅ Adding Authorization header');
    req = req.clone({
      setHeaders: {
        Authorization: `Bearer ${token}`
      }
    });
  } else {
    console.warn('  ❌ No token available - request will fail with 401');
  }

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {
      // Handle 401 Unauthorized errors globally
      if (error.status === 401) {
        console.error('🚨 401 Unauthorized - Token expired or invalid');
        console.log('🔄 Auto-logging out and redirecting to login...');
        
        // Clear authentication data
        authService.logout();
        
        // Redirect to login page
        router.navigate(['/login'], {
          queryParams: { 
            returnUrl: router.url,
            sessionExpired: 'true'
          }
        });
      }
      
      return throwError(() => error);
    })
  );
};
