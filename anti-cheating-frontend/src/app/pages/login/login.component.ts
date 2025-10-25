import { Component, ElementRef, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../Services/auth.service.service';
import { CameraService } from '../../Services/camera.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit, OnDestroy {
  @ViewChild('videoElement') videoElement!: ElementRef<HTMLVideoElement>;
  @ViewChild('canvasElement') canvasElement!: ElementRef<HTMLCanvasElement>;

  loginForm!: FormGroup;
  loading = false;
  errorMessage = '';
  successMessage = '';
  showCamera = false;
  capturedImage: string | null = null;

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private cameraService: CameraService,
    private router: Router,
    private route: ActivatedRoute
  ) {
    // Initialize form in constructor to avoid undefined errors
    this.loginForm = this.fb.group({
      username: ['', [Validators.required]],
      password: ['', [Validators.required]]
    });
  }

  ngOnInit(): void {
    // Check if already logged in
    if (this.authService.isLoggedIn) {
      this.redirectToDashboard();
      return;
    }
    
    // Check if session expired
    this.route.queryParams.subscribe(params => {
      if (params['sessionExpired'] === 'true') {
        this.errorMessage = '⏱️ Your session has expired. Please login again.';
        console.log('🔒 Session expired - showing message to user');
      }
    });
  }

  ngOnDestroy(): void {
    // Stop camera stream when component is destroyed
    if (this.videoElement?.nativeElement?.srcObject) {
      const stream = this.videoElement.nativeElement.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
    }
  }

  async toggleCamera(): Promise<void> {
    if (!this.showCamera) {
      try {
        this.errorMessage = '';
        console.log('🎥 Step 1: Requesting camera access...');
        console.log('Browser:', navigator.userAgent);
        
        // Check browser support
        if (!navigator.mediaDevices || !navigator.mediaDevices.getUserMedia) {
          this.errorMessage = 'Camera API not supported. Please use Chrome, Edge, or Firefox.';
          return;
        }
        
        // Show video container FIRST so Angular renders the element
        this.showCamera = true;
        console.log('📺 Step 2: Video container shown');
        
        // Wait for Angular change detection and DOM update
        await new Promise(resolve => setTimeout(resolve, 100));
        
        // Check if video element exists
        const videoEl = this.videoElement?.nativeElement;
        if (!videoEl) {
          console.error('❌ Video element not found in DOM');
          this.errorMessage = 'Video element not ready. Please refresh the page.';
          this.showCamera = false;
          return;
        }
        
        console.log('✅ Step 3: Video element found:', videoEl);
        
        // Request camera stream - Edge/Chrome compatible constraints
        console.log('🎥 Step 4: Requesting camera stream...');
        const stream = await navigator.mediaDevices.getUserMedia({
          video: {
            width: { min: 320, ideal: 640, max: 1280 },
            height: { min: 240, ideal: 480, max: 720 },
            facingMode: 'user'
          },
          audio: false
        });
        
        console.log('✅ Step 5: Stream obtained:', {
          id: stream.id,
          active: stream.active,
          tracks: stream.getTracks().length,
          videoTrack: stream.getVideoTracks()[0]?.label
        });
        
        // Attach stream to video element
        videoEl.srcObject = stream;
        console.log('📺 Step 6: Stream attached to video element');
        
        // Edge-specific video element setup
        videoEl.muted = true;
        videoEl.playsInline = true;
        videoEl.autoplay = true;
        
        // Wait for video to be ready
        await new Promise((resolve, reject) => {
          const timeout = setTimeout(() => reject(new Error('Video load timeout')), 5000);
          
          videoEl.onloadedmetadata = () => {
            clearTimeout(timeout);
            console.log('📊 Video metadata loaded, dimensions:', videoEl.videoWidth, 'x', videoEl.videoHeight);
            resolve(true);
          };
          
          videoEl.onerror = (e) => {
            clearTimeout(timeout);
            console.error('❌ Video error:', e);
            reject(new Error('Video element error'));
          };
        });
        
        // Force play
        await videoEl.play();
        console.log('▶️ Step 7: Video is now playing!');
        
      } catch (error: any) {
        console.error('❌ Camera error:', error);
        this.showCamera = false;
        
        if (error.name === 'NotAllowedError' || error.name === 'PermissionDeniedError') {
          this.errorMessage = '🚫 Camera permission denied. Click the 🔒 lock icon in address bar → Allow camera access';
        } else if (error.name === 'NotFoundError' || error.name === 'DevicesNotFoundError') {
          this.errorMessage = '📷 No camera detected. Please check if your webcam is connected and working.';
        } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
          this.errorMessage = '⚠️ Camera is being used by another application. Close Teams, Zoom, Skype, or other apps and try again.';
        } else if (error.name === 'OverconstrainedError') {
          this.errorMessage = '⚙️ Camera settings not supported. Try a different browser or update your camera drivers.';
        } else if (error.name === 'SecurityError') {
          this.errorMessage = '🔒 Security error. Please use HTTPS or localhost.';
        } else {
          this.errorMessage = '❌ Camera error: ' + (error.message || 'Unknown error. Try using Chrome or refresh the page.');
        }
      }
    } else {
      // Capture photo
      this.capturePhoto();
    }
  }

  capturePhoto(): void {
    try {
      const videoEl = this.videoElement?.nativeElement;
      if (!videoEl || !videoEl.srcObject) {
        this.errorMessage = 'Camera not active. Please start camera first.';
        return;
      }
      
      // Create canvas and capture image
      const canvas = document.createElement('canvas');
      canvas.width = videoEl.videoWidth;
      canvas.height = videoEl.videoHeight;
      
      const context = canvas.getContext('2d');
      if (context) {
        context.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
        this.capturedImage = canvas.toDataURL('image/jpeg', 0.8);
        console.log('📸 Photo captured successfully');
      } else {
        throw new Error('Could not get canvas context');
      }
      
      // Stop camera and hide video
      this.showCamera = false;
      const stream = videoEl.srcObject as MediaStream;
      stream.getTracks().forEach(track => track.stop());
      videoEl.srcObject = null;
      
    } catch (error: any) {
      this.errorMessage = 'Failed to capture image: ' + (error.message || 'Unknown error');
      console.error('❌ Capture error:', error);
    }
  }

  retakePhoto(): void {
    this.capturedImage = null;
    this.showCamera = false;
    this.errorMessage = '';
  }

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid) {
      Object.keys(this.loginForm.controls).forEach(key => {
        this.loginForm.get(key)?.markAsTouched();
      });
      return;
    }

    // Check if face verification is done
    if (!this.capturedImage) {
      this.errorMessage = 'Please capture your face for verification before signing in.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      const username = this.loginForm.get('username')?.value;
      const password = this.loginForm.get('password')?.value;
      
      console.log('Logging in with username:', username);
      console.log('Face image captured:', this.capturedImage ? 'Yes' : 'No');
      
      // Convert base64 image to Blob
      const imageBlob = this.base64ToBlob(this.capturedImage);
      
      // Create FormData for multipart/form-data request
      const formData = new FormData();
      formData.append('userName', username);
      formData.append('password', password); // Use actual password from form
      formData.append('image', imageBlob, 'face.jpg');
      
      // Send FormData directly to backend
      this.authService.loginWithFace(formData).subscribe({
        next: (response) => {
          this.loading = false;
          this.successMessage = '✅ Face verified! Login successful! Redirecting...';
          setTimeout(() => {
            this.redirectToDashboard();
          }, 1000);
        },
        error: (error) => {
          this.loading = false;
          this.errorMessage = error.error?.error || error.error?.message || 'Login failed. Please check your username and face verification.';
          console.error('Login error:', error);
        }
      });
    } catch (error) {
      this.loading = false;
      this.errorMessage = 'An unexpected error occurred. Please try again.';
      console.error('Login error:', error);
    }
  }

  // Helper method to convert base64 to Blob
  private base64ToBlob(base64: string): Blob {
    const parts = base64.split(';base64,');
    const contentType = parts[0].split(':')[1];
    const raw = window.atob(parts[1]);
    const rawLength = raw.length;
    const uInt8Array = new Uint8Array(rawLength);

    for (let i = 0; i < rawLength; ++i) {
      uInt8Array[i] = raw.charCodeAt(i);
    }

    return new Blob([uInt8Array], { type: contentType });
  }

  private redirectToDashboard(): void {
    if (this.authService.isAdmin) {
      this.router.navigate(['/admin/dashboard']);
    } else {
      this.router.navigate(['/exam-dashboard']);
    }
  }
}
