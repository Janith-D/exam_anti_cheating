import { CommonModule } from '@angular/common';
import { Component, ElementRef, ViewChild, ChangeDetectorRef } from '@angular/core';
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
  audioSamples: Blob[] = [];
  recordingStatus = '';
  errorMessage = '';
  successMessage = '';

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private cdr: ChangeDetectorRef
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

      // --- CAPTURE FACE IMAGE INSTANTLY ---
      // We do this BEFORE the voice recording so the picture is guaranteed 
      const canvas = document.createElement('canvas');
      canvas.width = videoEl.videoWidth || 640;
      canvas.height = videoEl.videoHeight || 480;
      const context = canvas.getContext('2d');
      if (context) {
        context.drawImage(videoEl, 0, 0, canvas.width, canvas.height);
        this.capturedImage = canvas.toDataURL('image/jpeg', 0.85);
      } else {
        this.errorMessage = 'Could not capture image from camera.';
        return;
      }

      this.audioSamples = [];
      const stream = videoEl.srcObject as MediaStream;
      
      const audioTracks = stream.getAudioTracks();
      if (audioTracks.length === 0 || audioTracks[0].readyState !== 'live') {
        throw new Error('Microphone access is required but no audio track was found or it is inactive.');
      }
      
      await this.recordSingleSentence(stream);

      this.recordingStatus = '';
      this.successMessage = '🎙️ Voice captured completely. You may now submit the form.';
      this.showCamera = false;
      stream.getTracks().forEach(track => track.stop());
      videoEl.srcObject = null;
      this.cdr.detectChanges();

    } catch (error: any) {
      this.errorMessage = error?.message || 'Media capture failed';
      this.recordingStatus = '';
      this.showCamera = false;
      this.cdr.detectChanges();
    }
  }

  private recordSingleSentence(stream: MediaStream): Promise<void> {
    return new Promise((resolve, reject) => {
      this.recordingStatus = `Recording Voice (3.5s)... Please read the phrase aloud.`;
      this.cdr.detectChanges();

      const audioChunks: Blob[] = [];
      // Record audio-only in WebM (browser-native); will be converted to WAV after stop.
      // soundfile does NOT support WebM — we use AudioContext to decode, then re-encode as WAV.
      const audioOnlyStream = new MediaStream(stream.getAudioTracks());
      const mediaRecorder = new MediaRecorder(audioOnlyStream);

      mediaRecorder.onerror = (e: any) => {
        reject(new Error('Media recorder error: ' + (e.error?.message || e.message)));
      };

      mediaRecorder.ondataavailable = (e) => {
        if (e.data.size > 0) audioChunks.push(e.data);
      };

      mediaRecorder.onstop = async () => {
        try {
          const webmBlob = new Blob(audioChunks, { type: mediaRecorder.mimeType || 'audio/webm' });
          const wavBlob = await this.convertBlobToWav(webmBlob);
          this.audioSamples.push(wavBlob);
          resolve();
        } catch (err: any) {
          reject(new Error('Audio conversion failed: ' + (err.message || err)));
        }
      };

      mediaRecorder.start();
      setTimeout(() => {
        if (mediaRecorder.state !== 'inactive') {
          mediaRecorder.stop();
        }
      }, 3500); // 3.5 seconds per sample
    });
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

  retakePhoto(): void {
    this.capturedImage = null;
    this.audioSamples = [];
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
      this.errorMessage = 'Please fill out all required fields correctly (e.g., matching passwords, valid email, minimum length).';
      Object.keys(this.registerForm.controls).forEach(key => {
        this.registerForm.get(key)?.markAsTouched();
      });
      return;
    }

    if (!this.capturedImage || this.audioSamples.length === 0) {
      this.errorMessage = 'Please capture your face photo and complete the 3 voice phrases before registering.';
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
    
    this.audioSamples.forEach((blob, i) => {
      formData.append('audio', blob, `audio_${i + 1}.wav`);
    });

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
