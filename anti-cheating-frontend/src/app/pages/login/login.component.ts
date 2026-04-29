import { Component, ElementRef, OnDestroy, OnInit, ViewChild, ChangeDetectorRef } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators, FormsModule } from '@angular/forms';
import { Router, RouterModule, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../Services/auth.service.service';
import { CameraService } from '../../Services/camera.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule, FormsModule, RouterModule],
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
  audioBase64: string | null = null;
  recordingStatus = '';
  adminLogin = false; // Flag for admin login without face verification

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private cameraService: CameraService,
    private router: Router,
    private route: ActivatedRoute,
    private cdr: ChangeDetectorRef
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
        const reason = params['reason'];
        if (reason === 'backend_restart') {
          this.errorMessage = '🔄 Server was restarted. Please login again to continue.';
        } else {
          this.errorMessage = '⏱️ Your session has expired. Please login again.';
        }
        console.log('🔒 Session expired - showing message to user. Reason:', reason);
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
        // Check secure context (required except for localhost)
        if (!(window.isSecureContext || location.hostname === 'localhost' || location.hostname === '127.0.0.1')) {
          console.warn('Insecure context detected:', location.protocol, location.hostname);
          this.errorMessage = '🔒 Camera requires HTTPS or localhost. Please use https:// or run locally on localhost.';
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
        // Stop any existing stream to avoid NotReadable errors
        this.stopVideoStreamIfAny(videoEl);
        
        // Request camera stream - Edge/Chrome compatible constraints
        console.log('🎥 Step 4: Requesting camera stream...');
        let stream: MediaStream | null = null;
        try {
          stream = await navigator.mediaDevices.getUserMedia({
            video: {
              width: { min: 320, ideal: 640, max: 1280 },
              height: { min: 240, ideal: 480, max: 720 },
              facingMode: 'user'
            },
            audio: true
          });
        } catch (primaryErr: any) {
          console.warn('Primary getUserMedia failed, trying fallback via CameraService...', primaryErr);
          try {
            stream = await this.cameraService.startCamera();
          } catch (fallbackErr: any) {
            console.error('Fallback camera start failed:', fallbackErr);
            throw primaryErr; // rethrow the original for accurate error mapping below
          }
        }
        
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
        videoEl.setAttribute('muted', 'true');
        (videoEl as any).playsInline = true;
        videoEl.setAttribute('playsinline', 'true');
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
          this.errorMessage = '📷 No camera detected. Please check if your webcam is connected and working. If using a desktop PC, verify drivers.';
        } else if (error.name === 'NotReadableError' || error.name === 'TrackStartError') {
          this.errorMessage = '⚠️ Camera is being used by another application. Close Teams, Zoom, Skype, or other apps and try again.';
        } else if (error.name === 'OverconstrainedError') {
          this.errorMessage = '⚙️ Camera settings not supported. Try a different browser or update your camera drivers.';
        } else if (error.name === 'SecurityError') {
          this.errorMessage = '🔒 Security error. Please use HTTPS or localhost.';
        } else {
          this.errorMessage = '❌ Camera error: ' + (error.message || 'Unknown error. Try using Chrome or refresh the page.');
        }
        // Enumerate devices to aid troubleshooting
        try {
          const devices = await navigator.mediaDevices.enumerateDevices();
          const cams = devices.filter(d => d.kind === 'videoinput');
          console.log('📦 Media devices:', devices);
          console.log('📷 Video inputs found:', cams.map(c => ({ label: c.label, deviceId: c.deviceId })));
        } catch (e) {
          console.warn('Failed to enumerate devices:', e);
        }
      }
    } else {
      // Capture photo
      this.capturePhoto();
    }
  }

  private stopVideoStreamIfAny(videoEl: HTMLVideoElement): void {
    const existing = videoEl.srcObject as MediaStream | null;
    if (existing) {
      existing.getTracks().forEach(t => t.stop());
      videoEl.srcObject = null;
    }
  }

  async capturePhoto(): Promise<void> {
    try {
      const videoEl = this.videoElement?.nativeElement;
      if (!videoEl || !videoEl.srcObject) {
        this.errorMessage = 'Camera not active. Please start camera first.';
        return;
      }

      // --- CAPTURE FACE IMAGE INSTANTLY ---
      // Fixes the issue where taking the pic 3 seconds later resulted in a black canvas
      const canvas = document.createElement('canvas');
      canvas.width = videoEl.videoWidth || 640;
      canvas.height = videoEl.videoHeight || 480;

      const context = canvas.getContext('2d');
      if (context) {
        context.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
        this.capturedImage = canvas.toDataURL('image/jpeg', 0.8);
        console.log('📸 Face image captured instantly');
      } else {
        console.error('Could not get canvas context');
        this.errorMessage = 'Could not capture image from camera.';
        return;
      }

      this.recordingStatus = 'Recording Voice (3.5s)... Please read the phrase aloud.';
      const stream = videoEl.srcObject as MediaStream;
      
      const audioTracks = stream.getAudioTracks();
      if (audioTracks.length === 0 || audioTracks[0].readyState !== 'live') {
        throw new Error('Microphone access is required but no audio track was found or it is inactive.');
      }

      // Record audio-only. WebM is browser-native; we convert to WAV after recording
      // because soundfile (Python) does NOT support WebM but does support WAV.
      const audioOnlyStream = new MediaStream(stream.getAudioTracks());
      const audioChunks: Blob[] = [];
      const mediaRecorder = new MediaRecorder(audioOnlyStream);

      mediaRecorder.onerror = (e: any) => {
        this.errorMessage = 'Media recorder error: ' + (e.error?.message || e.message);
        this.recordingStatus = '';
        this.showCamera = false;
        stream.getTracks().forEach(t => t.stop());
      };

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunks.push(e.data);
      };

      mediaRecorder.onstop = async () => {
        const webmBlob = new Blob(audioChunks, { type: mediaRecorder.mimeType || 'audio/webm' });
        // Convert WebM → WAV so Python's soundfile can decode actual speech features
        const wavBlob = await this.convertBlobToWav(webmBlob);
        const reader = new FileReader();
        reader.readAsDataURL(wavBlob);
        reader.onloadend = () => {
          this.audioBase64 = reader.result as string;
          console.log('🎙️ Voice audio captured and converted to WAV');

          this.recordingStatus = '';
          this.successMessage = '🎙️ Voice captured! You may now sign in.';
          this.showCamera = false;
          stream.getTracks().forEach(track => track.stop());
          videoEl.srcObject = null;
          this.cdr.detectChanges();
        };
      };

      mediaRecorder.start();

      setTimeout(() => {
        if (mediaRecorder.state !== 'inactive') {
          mediaRecorder.stop();
        }
      }, 3500);

    } catch (error: any) {
      this.errorMessage = 'Failed to capture image/audio: ' + (error.message || 'Unknown error');
      console.error('❌ Capture error:', error);
      this.recordingStatus = '';
    }
  }

  retakePhoto(): void {
    this.capturedImage = null;
    this.audioBase64 = null;
    this.showCamera = false;
    this.errorMessage = '';
    this.successMessage = '';
  }

  onGoogleSignIn(): void {
    this.errorMessage = '';
    const started = this.authService.startGoogleSignIn();

    if (!started) {
      this.errorMessage = 'Google sign-in is not configured yet. Set Google OAuth settings first.';
    }
  }

  async onSubmit(): Promise<void> {
    if (this.loginForm.invalid) {
      Object.keys(this.loginForm.controls).forEach(key => {
        this.loginForm.get(key)?.markAsTouched();
      });
      return;
    }

    if (!this.adminLogin && (!this.capturedImage || !this.audioBase64)) {
      this.errorMessage = 'Please capture your face and complete the voice recording before signing in.';
      return;
    }

    this.loading = true;
    this.errorMessage = '';
    this.successMessage = '';

    try {
      const username = this.loginForm.get('username')?.value;
      const password = this.loginForm.get('password')?.value;

      const formData = new FormData();
      formData.append('userName', username);
      formData.append('password', password);

      if (this.capturedImage) {
        const imageBlob = this.base64ToBlob(this.capturedImage);
        formData.append('image', imageBlob, 'face.jpg');
        
        if (this.audioBase64) {
          const audioBlob = this.base64ToBlob(this.audioBase64);
          formData.append('audio', audioBlob, 'audio.webm');
        }
      } else {
        const dummyBlob = this.createDummyImage();
        formData.append('image', dummyBlob, 'dummy.png');
      }

      this.authService.loginWithFace(formData).subscribe({
        next: (response) => {
          this.loading = false;
          this.successMessage = '✅ Face recognized! Login successful! Redirecting...';
          setTimeout(() => {
            this.redirectToDashboard();
          }, 1000);
        },
        error: (error) => {
          this.loading = false;
          console.error('Login error details:', error);
          
          // Handle specific error messages
          const errorMsg = error.error?.error || error.error?.message || '';
          
          if (errorMsg.includes('Bad credentials') || error.status === 400) {
            this.errorMessage = '❌ Invalid username or password. Please check your credentials or register a new account.';
          } else if (errorMsg.includes('Face verification failed')) {
            this.errorMessage = '🔍 Face recognition failed. Please capture a clear face image and try again.';
          } else if (errorMsg.includes('not found')) {
            this.errorMessage = '👤 User not found. Please register first or check your username.';
          } else if (error.status === 401) {
            this.errorMessage = '🔒 Authentication failed. Invalid username or password.';
          } else if (error.status === 0) {
            this.errorMessage = '🌐 Cannot connect to server. Please check if the backend is running on http://localhost:8080';
          } else {
            this.errorMessage = errorMsg || 'Login failed. Please check your username and password.';
          }
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

  /** Decode any browser-supported audio Blob and re-encode as 16-bit PCM WAV */
  private async convertBlobToWav(blob: Blob): Promise<Blob> {
    const arrayBuffer = await blob.arrayBuffer();
    const audioCtx = new AudioContext();
    const audioBuffer = await audioCtx.decodeAudioData(arrayBuffer);
    audioCtx.close();
    const wavBuffer = this.audioBufferToWav(audioBuffer);
    return new Blob([wavBuffer], { type: 'audio/wav' });
  }

  /** Encode an AudioBuffer as 16-bit PCM WAV (mono, native sample rate) */
  private audioBufferToWav(audioBuffer: AudioBuffer): ArrayBuffer {
    const numChannels = 1;
    const sampleRate = audioBuffer.sampleRate;
    const channelData = audioBuffer.getChannelData(0);
    const samples = channelData.length;
    const bitsPerSample = 16;
    const blockAlign = numChannels * (bitsPerSample / 8);
    const byteRate = sampleRate * blockAlign;
    const dataSize = samples * blockAlign;
    const buffer = new ArrayBuffer(44 + dataSize);
    const view = new DataView(buffer);
    const writeStr = (off: number, s: string) => { for (let i = 0; i < s.length; i++) view.setUint8(off + i, s.charCodeAt(i)); };
    writeStr(0, 'RIFF');  view.setUint32(4, 36 + dataSize, true);
    writeStr(8, 'WAVE'); writeStr(12, 'fmt ');
    view.setUint32(16, 16, true);   // PCM chunk size
    view.setUint16(20, 1, true);    // PCM format
    view.setUint16(22, numChannels, true);
    view.setUint32(24, sampleRate, true);
    view.setUint32(28, byteRate, true);
    view.setUint16(32, blockAlign, true);
    view.setUint16(34, bitsPerSample, true);
    writeStr(36, 'data'); view.setUint32(40, dataSize, true);
    let off = 44;
    for (let i = 0; i < samples; i++) {
      const s = Math.max(-1, Math.min(1, channelData[i]));
      view.setInt16(off, s < 0 ? s * 0x8000 : s * 0x7FFF, true);
      off += 2;
    }
    return buffer;
  }

  private createDummyImage(): Blob {
    // Create a 1x1 transparent PNG (smallest valid image)
    const base64 = 'iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==';
    const binary = atob(base64);
    const array = new Uint8Array(binary.length);
    for (let i = 0; i < binary.length; i++) {
      array[i] = binary.charCodeAt(i);
    }
    return new Blob([array], { type: 'image/png' });
  }

  private redirectToDashboard(): void {
    if (this.authService.isAdmin) {
      this.router.navigate(['/admin/dashboard']);
    } else {
      this.router.navigate(['/exam-dashboard']);
    }
  }
}

