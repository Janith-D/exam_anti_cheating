import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AuthService } from '../../Services/auth.service.service';

@Component({
  selector: 'app-oauth2-callback',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './oauth2-callback.component.html',
  styleUrl: './oauth2-callback.component.css'
})
export class OAuth2CallbackComponent implements OnInit {
  loading = true;
  errorMessage = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private authService: AuthService
  ) {}

  ngOnInit(): void {
    this.route.queryParamMap.subscribe(params => {
      const error = params.get('error');
      const token = params.get('token');
      const userId = params.get('userId');
      const userName = params.get('userName');
      const email = params.get('email');
      const role = params.get('role');

      if (error) {
        this.loading = false;
        this.errorMessage = decodeURIComponent(error);
        setTimeout(() => this.router.navigate(['/login']), 1800);
        return;
      }

      if (!token || !userId || !userName || !email || !role) {
        this.loading = false;
        this.errorMessage = 'Google login failed: missing callback data.';
        setTimeout(() => this.router.navigate(['/login']), 1800);
        return;
      }

      this.authService.completeOAuthLogin({
        token,
        userId: Number(userId),
        userName,
        email,
        role
      });

      if (role.includes('ADMIN')) {
        this.router.navigate(['/admin/dashboard']);
      } else {
        this.router.navigate(['/exam-dashboard']);
      }
    });
  }
}
