import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthService } from '../Services/auth.service.service';

// Flag to prevent multiple concurrent logout operations
let isLoggingOut = false;

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
      // Handle 401 Unauthorized errors - but ONLY for actual authentication failures
      if (error.status === 401) {
        console.error('🚨 401 Unauthorized - Checking error type...');
        console.error('  Error details:', error.error);
        
        // Check if this is a genuine JWT/authentication error (expired token, invalid token, etc.)
        const isAuthenticationError = 
          error.error?.error?.toLowerCase().includes('jwt') || 
          error.error?.message?.toLowerCase().includes('jwt') ||
          error.error?.error?.toLowerCase().includes('token') ||
          error.error?.message?.toLowerCase().includes('token') ||
          error.error?.error?.toLowerCase().includes('expired') ||
          error.error?.message?.toLowerCase().includes('expired') ||
          error.error?.error?.toLowerCase().includes('signature') ||
          error.error?.message?.toLowerCase().includes('signature') ||
          error.error?.error?.toLowerCase().includes('malformed') ||
          !token; // No token at all means not logged in
        
        // Check if this is likely due to backend restart
        const isBackendRestartIssue = 
          error.error?.error?.includes('JWT') || 
          error.error?.message?.includes('JWT') ||
          error.error?.error?.includes('Invalid') ||
          error.error?.message?.includes('signature');
        
        // ONLY logout if it's an actual authentication error, NOT authorization/permission errors
        if (isAuthenticationError && !isLoggingOut) {
          isLoggingOut = true;
          
          const message = isBackendRestartIssue 
            ? 'Backend was restarted. Please login again.'
            : 'Your session has expired. Please login again.';
          
          console.log('🔄 Genuine authentication error - Auto-logging out:', message);
          
          // Clear authentication data
          authService.logout();
          
          // Redirect to login page
          router.navigate(['/login'], {
            queryParams: { 
              returnUrl: router.url,
              sessionExpired: 'true',
              reason: isBackendRestartIssue ? 'backend_restart' : 'token_expired'
            }
          }).then(() => {
            // Reset flag after navigation completes
            setTimeout(() => {
              isLoggingOut = false;
            }, 1000);
          });
        } else if (!isAuthenticationError) {
          console.log('⚠️ 401 error but NOT an authentication issue - letting component handle it');
          // This is a permission/authorization error, let the component handle it
          // Don't logout, don't redirect - just pass the error through
        } else {
          console.log('🔄 Logout already in progress, skipping...');
        }
      }
      
      return throwError(() => error);
    })
  );
};
