import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class CameraService {
  private stream: MediaStream | null = null;

  constructor() { }

  // Start camera and get video stream
  async startCamera(): Promise<MediaStream> {
    try {
      // Stop any existing stream first
      this.stopCamera();
      
      console.log('Requesting camera access with constraints...');
      
      this.stream = await navigator.mediaDevices.getUserMedia({
        video: {
          width: { min: 640, ideal: 1280, max: 1920 },
          height: { min: 480, ideal: 720, max: 1080 },
          facingMode: 'user'
        },
        audio: false
      });
      
      console.log('Camera stream obtained successfully:', {
        id: this.stream.id,
        active: this.stream.active,
        tracks: this.stream.getTracks().length
      });
      
      return this.stream;
    } catch (error: any) {
      console.error('Error accessing camera:', error);
      console.error('Error details:', {
        name: error.name,
        message: error.message,
        constraint: error.constraint
      });
      throw error;
    }
  }

  // Stop camera
  stopCamera(): void {
    if (this.stream) {
      this.stream.getTracks().forEach(track => track.stop());
      this.stream = null;
    }
  }

  // Capture image from video element
  captureImage(videoElement: HTMLVideoElement): string {
    const canvas = document.createElement('canvas');
    canvas.width = videoElement.videoWidth;
    canvas.height = videoElement.videoHeight;
    
    const context = canvas.getContext('2d');
    if (context) {
      context.drawImage(videoElement, 0, 0, canvas.width, canvas.height);
      return canvas.toDataURL('image/jpeg', 0.8);
    }
    
    throw new Error('Failed to capture image');
  }

  // Convert base64 to blob
  base64ToBlob(base64: string): Blob {
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

  // Capture multiple frames with instructions for liveness detection
  async captureFramesWithLiveness(
    videoElement: HTMLVideoElement,
    numFrames: number = 5,
    delayBetweenFrames: number = 1000
  ): Promise<string[]> {
    const frames: string[] = [];
    const instructions = [
      '👀 Look at camera',
      '👁️ Blink your eyes', 
      '↔️ Move head slightly left',
      '↔️ Move head slightly right',
      '👀 Look at camera again'
    ];

    for (let i = 0; i < numFrames; i++) {
      const instruction = instructions[i] || 'Hold steady';
      
      // Show instruction (caller should display this)
      console.log(`Frame ${i + 1}/${numFrames}: ${instruction}`);
      
      // Wait for specified delay
      if (i > 0) {
        await this.delay(delayBetweenFrames);
      }
      
      // Capture frame
      try {
        const frame = this.captureImage(videoElement);
        frames.push(frame);
        console.log(`✅ Frame ${i + 1} captured`);
      } catch (error) {
        console.error(`❌ Failed to capture frame ${i + 1}:`, error);
        throw error;
      }
    }

    return frames;
  }

  // Get instruction for specific frame number
  getLivenessInstruction(frameIndex: number): string {
    const instructions = [
      '👀 Look at camera',
      '👁️ Blink your eyes',
      '↔️ Move head slightly left', 
      '↔️ Move head slightly right',
      '👀 Look at camera again'
    ];
    return instructions[frameIndex] || 'Hold steady';
  }

  // Helper delay function
  private delay(ms: number): Promise<void> {
    return new Promise(resolve => setTimeout(resolve, ms));
  }
}
