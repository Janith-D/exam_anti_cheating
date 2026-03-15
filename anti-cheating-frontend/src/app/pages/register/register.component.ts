import { CommonModule } from '@angular/common';
import { Component, ElementRef, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule } from '@angular/router';
import { AuthService } from '../../Services/auth.service.service';

@Component({
  selector: 'app-register',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './register.component.html',
  styleUrl: './register.component.css'
})
export class RegisterComponent {
  @ViewChild('videoElement') videoElement!: ElementRef<HTMLVideoElement>;

  registerForm: FormGroup;
  loading = false;
  showCamera = false;
  capturedImage: string | null = null;
  errorMessage = '';
  successMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router
  ) {
    this.registerForm = this.fb.group({
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      username: ['', [Validators.required, Validators.minLength(3)]],
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      confirmPassword: ['', [Validators.required]]
    });
  }

  passwordsMismatch(): boolean {
    const password = this.registerForm.get('password')?.value;
    const confirmPassword = this.registerForm.get('confirmPassword')?.value;
    return !!password && !!confirmPassword && password !== confirmPassword;
  }

  async toggleCamera(): Promise<void> {
    if (!this.showCamera) {
      try {
        this.errorMessage = '';
        this.showCamera = true;
        await new Promise(resolve => setTimeout(resolve, 100));

        const videoEl = this.videoElement?.nativeElement;
        if (!videoEl) {
          this.showCamera = false;
          this.errorMessage = 'Video element is not ready. Refresh the page and try again.';
          return;
        }

        const stream = await navigator.mediaDevices.getUserMedia({
          video: { width: { ideal: 640 }, height: { ideal: 480 }, facingMode: 'user' },
          audio: false
        });

        videoEl.srcObject = stream;
        videoEl.muted = true;
        videoEl.setAttribute('playsinline', 'true');
        await videoEl.play();
      } catch (error: any) {
        this.showCamera = false;
        this.errorMessage = error?.message || 'Failed to access camera.';
      }
    } else {
      this.capturePhoto();
    }
  }

  capturePhoto(): void {
    try {
      const videoEl = this.videoElement?.nativeElement;
      if (!videoEl || !videoEl.srcObject) {
        this.errorMessage = 'Camera is not active.';
        return;
      }

      const canvas = document.createElement('canvas');
      canvas.width = videoEl.videoWidth;
      canvas.height = videoEl.videoHeight;
      const context = canvas.getContext('2d');

      if (!context) {
        this.errorMessage = 'Could not capture image from camera.';
        return;
      }

      context.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
      this.capturedImage = canvas.toDataURL('image/jpeg', 0.85);

      const stream = videoEl.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      videoEl.srcObject = null;
      this.showCamera = false;
    } catch (error: any) {
      this.errorMessage = error?.message || 'Failed to capture face image.';
    }
  }

  retakePhoto(): void {
    this.capturedImage = null;
    this.showCamera = false;
    this.errorMessage = '';
  }

  onGoogleSignIn(): void {
    this.errorMessage = '';
    const started = this.authService.startGoogleSignIn();

    if (!started) {
      this.errorMessage = 'Google sign-in is not configured yet. Set Google OAuth settings first.';
    }
  }

  onSubmit(): void {
    if (this.registerForm.invalid || this.passwordsMismatch()) {
      Object.keys(this.registerForm.controls).forEach(key => {
        this.registerForm.get(key)?.markAsTouched();
      });
      return;
    }

    if (!this.capturedImage) {
      this.errorMessage = 'Please capture your face photo before registering.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    const formData = new FormData();
    formData.append('userName', this.registerForm.get('username')?.value);
    formData.append('password', this.registerForm.get('password')?.value);
    formData.append('role', 'STUDENT');
    formData.append('firstName', this.registerForm.get('firstName')?.value);
    formData.append('lastName', this.registerForm.get('lastName')?.value);
    formData.append('email', this.registerForm.get('email')?.value);

    const imageBlob = this.base64ToBlob(this.capturedImage);
    formData.append('image', imageBlob, 'face.jpg');

    this.authService.registerWithFace(formData).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Registration completed successfully. Redirecting to login...';
        setTimeout(() => this.router.navigate(['/login']), 1200);
      },
      error: (error) => {
        this.loading = false;
        const msg = error?.error?.error || 'Registration failed. Please try again.';
        this.errorMessage = msg;
      }
    });
  }

  private base64ToBlob(base64: string): Blob {
    const parts = base64.split(';base64,');
    const contentType = parts[0].split(':')[1] || 'image/jpeg';
    const raw = window.atob(parts[1]);
    const rawLength = raw.length;
    const uInt8Array = new Uint8Array(rawLength);

    for (let i = 0; i < rawLength; i++) {
      uInt8Array[i] = raw.charCodeAt(i);
    }

    return new Blob([uInt8Array], { type: contentType });
  }

}
