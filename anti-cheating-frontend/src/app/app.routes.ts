import { Routes } from '@angular/router';
import { authGuard, adminGuard, studentGuard } from './guards/auth.guard';

export const routes: Routes = [
  // Public routes
  {
    path: '',
    redirectTo: '/login',
    pathMatch: 'full'
  },
  {
    path: 'login',
    loadComponent: () => import('./pages/login/login.component').then(m => m.LoginComponent)
  },
  {
    path: 'register',
    loadComponent: () => import('./pages/register/register.component').then(m => m.RegisterComponent)
  },
  {
    path: 'oauth2/callback',
    loadComponent: () => import('./pages/oauth2-callback/oauth2-callback.component').then(m => m.OAuth2CallbackComponent)
  },

  // Student routes
  {
    path: 'dashboard',
    redirectTo: '/exam-dashboard',
    pathMatch: 'full'
  },
  {
    path: 'test-dashboard',
    redirectTo: '/exam-dashboard',
    pathMatch: 'full'
  },
  {
    path: 'exam-dashboard',
    loadComponent: () => import('./pages/exam-dashboard/exam-dashboard.component').then(m => m.ExamDashboardComponent)
  },
  {
    path: 'exam-details/:id',
    loadComponent: () => import('./pages/exam-details/exam-details.component').then(m => m.ExamDetailsComponent)
  },
  {
    path: 'test/:id',
    loadComponent: () => import('./pages/test-page/test-page.component').then(m => m.TestPageComponent)
  },
  {
    path: 'test-page/:id',
    loadComponent: () => import('./pages/test-page/test-page.component').then(m => m.TestPageComponent)
  },
  {
    path: 'results',
    loadComponent: () => import('./pages/user-results/user-results.component').then(m => m.UserResultsComponent)
  },

  // Admin routes
  {
    path: 'admin/dashboard',
    loadComponent: () => import('./pages/proctor-dashboard/proctor-dashboard.component').then(m => m.ProctorDashboardComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/exams',
    loadComponent: () => import('./pages/admin-exam-management/admin-exam-management.component').then(m => m.AdminExamManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/test-management',
    loadComponent: () => import('./pages/test-management/test-management.component').then(m => m.TestManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/test-management/:id',
    loadComponent: () => import('./pages/test-management/test-management.component').then(m => m.TestManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/tests',
    loadComponent: () => import('./pages/test-management/test-management.component').then(m => m.TestManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/alerts',
    loadComponent: () => import('./pages/alert-dashboard/alert-dashboard.component').then(m => m.AlertDashboardComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/alerts/:id',
    loadComponent: () => import('./pages/alert-dashboard/alert-dashboard.component').then(m => m.AlertDashboardComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/students',
    loadComponent: () => import('./pages/student-management/student-management.component').then(m => m.StudentManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/students/:id',
    loadComponent: () => import('./pages/student-management/student-management.component').then(m => m.StudentManagementComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/screenshots',
    loadComponent: () => import('./pages/screenshot-viewer/screenshot-viewer.component').then(m => m.ScreenshotViewerComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/screenshots/student/:studentId',
    loadComponent: () => import('./pages/screenshot-viewer/screenshot-viewer.component').then(m => m.ScreenshotViewerComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/screenshots/:sessionId',
    loadComponent: () => import('./pages/screenshot-viewer/screenshot-viewer.component').then(m => m.ScreenshotViewerComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/results/:testId',
    loadComponent: () => import('./pages/test-results/test-results.component').then(m => m.TestResultsComponent),
    canActivate: [adminGuard]
  },

  // Fallback
  {
    path: '**',
    redirectTo: '/login'
  }
];
