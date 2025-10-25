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

  // Student routes
  {
    path: 'dashboard',
    loadComponent: () => import('./pages/test-dashboard/test-dashboard.component').then(m => m.TestDashboardComponent)
  },
  {
    path: 'test-dashboard',
    loadComponent: () => import('./pages/test-dashboard/test-dashboard.component').then(m => m.TestDashboardComponent)
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
    loadComponent: () => import('./pages/alert-dashboard/alert-dashboard.component').then(m => m.AlertDashboardComponent),
    canActivate: [adminGuard]
  },
  {
    path: 'admin/sessions',
    loadComponent: () => import('./pages/exam-session-management/exam-session-management.component').then(m => m.ExamSessionManagementComponent),
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
