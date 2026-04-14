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
  audioBase64: string | null = null;
  recordingStatus = '';
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
          audio: true
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

  async capturePhoto(): Promise<void> {
    try {
      const videoEl = this.videoElement?.nativeElement;
      if (!videoEl || !videoEl.srcObject) {
        this.errorMessage = 'Camera is not active.';
        return;
      }

      this.recordingStatus = 'Recording Voice (3s)... Please say: "My name is [Your Name]"';
      const stream = videoEl.srcObject as MediaStream;
      
      const audioTracks = stream.getAudioTracks();
      if (audioTracks.length === 0 || audioTracks[0].readyState !== 'live') {
        throw new Error('Microphone access is required but no audio track was found or it is inactive.');
      }
      
      // Use the raw stream to prevent "Failed to execute 'start'..." on stripped audio streams
      const audioChunks: Blob[] = [];
      const mediaRecorder = new MediaRecorder(stream);
      
      mediaRecorder.onerror = (e: any) => {
        this.errorMessage = 'Media recorder error: ' + (e.error?.message || e.message);
        this.recordingStatus = '';
        this.showCamera = false;
        stream.getTracks().forEach(t => t.stop());
      };

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunks.push(e.data);
      };

      mediaRecorder.onstop = () => {
        const audioBlob = new Blob(audioChunks);
        const reader = new FileReader();
        reader.readAsDataURL(audioBlob);
        reader.onloadend = () => {
          this.audioBase64 = reader.result as string;
          
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

          this.recordingStatus = '';
          this.showCamera = false;
          stream.getTracks().forEach(track => track.stop());
          videoEl.srcObject = null;
        };
      };

      mediaRecorder.start();
      setTimeout(() => {
        if (mediaRecorder.state !== 'inactive') {
          mediaRecorder.stop();
        }
      }, 3000);

    } catch (error: any) {
      this.errorMessage = error?.message || 'Failed to capture face image and audio.';
      this.recordingStatus = '';
    }
  }

  retakePhoto(): void {
    this.capturedImage = null;
    this.audioBase64 = null;
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

    if (!this.capturedImage || !this.audioBase64) {
      this.errorMessage = 'Please capture your face photo and voice before registering.';
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
    
    // Append audio file 
    // We already have the audio stored in Base64
    formData.append('audio', this.audioBase64);

    this.authService.registerWithFace(formData).subscribe({
      next: () => {
        this.loading = false;
        this.successMessage = 'Registration completed successfully. Redirecting to login...';
        setTimeout(() => this.router.navigate(['/login']), 1200);
      },
      error: (error: any) => {
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
