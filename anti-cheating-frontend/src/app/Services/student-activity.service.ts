import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import SockJS from 'sockjs-client';
import { Client } from '@stomp/stompjs';

export interface StudentActivity {
  studentId?: number;
  studentName?: string;
  studentEmail?: string;
  sessionId?: number;
  testId?: number;
  testName?: string;
  activityType: string;
  severity: string;
  description: string;
  metadata?: any;
  timestamp?: string;
  serverTimestamp?: string;
}

@Injectable({
  providedIn: 'root'
})
export class StudentActivityService {
  private stompClient: Client | null = null;
  private activitySubject = new Subject<StudentActivity>();
  private connected = false;

  constructor() {}

  /**
   * Connect to WebSocket for sending student activities
   */
  connect(): void {
    if (this.connected && this.stompClient?.connected) {
      console.log('✅ Already connected to Student Activity WebSocket');
      return;
    }

    console.log('🔌 Connecting to Student Activity WebSocket...');
    
    try {
      const socket = new SockJS('http://localhost:8080/ws');
      this.stompClient = new Client({
        webSocketFactory: () => socket as any,
        reconnectDelay: 5000,
        heartbeatIncoming: 4000,
        heartbeatOutgoing: 4000,
        debug: (str) => {
          // Log debug messages for troubleshooting
          if (str.includes('ERROR') || str.includes('DISCONNECT')) {
            console.error('🔴 WebSocket:', str);
          }
        }
      });

      this.stompClient.onConnect = (frame) => {
        this.connected = true;
        console.log('🟢 Student Activity WebSocket Connected:', frame);
      };

      this.stompClient.onStompError = (error: any) => {
        console.error('❌ Student Activity WebSocket Error:', error);
        this.connected = false;
      };
      
      this.stompClient.onWebSocketClose = (event) => {
        console.warn('⚠️ WebSocket closed:', event);
        this.connected = false;
      };

      this.stompClient.activate();
    } catch (error) {
      console.error('❌ Error creating WebSocket connection:', error);
      this.connected = false;
    }
  }

  /**
   * Send student activity to backend
   */
  sendActivity(activity: StudentActivity): void {
    if (!this.stompClient || !this.connected) {
      console.warn('WebSocket not connected. Attempting to reconnect...');
      this.connect();
      // Queue the activity and retry after connection
      setTimeout(() => this.sendActivity(activity), 1000);
      return;
    }

    try {
      // Add client timestamp if not present
      if (!activity.timestamp) {
        activity.timestamp = new Date().toISOString();
      }

      console.log('📤 Sending student activity:', activity.activityType, activity);

      this.stompClient.publish({
        destination: '/app/student-activity',
        body: JSON.stringify(activity)
      });

      console.log('✅ Activity sent successfully');
    } catch (error) {
      console.error('❌ Error sending activity:', error);
    }
  }

  /**
   * Subscribe to incoming activities (for admin dashboard)
   */
  subscribeToActivities(): Observable<StudentActivity> {
    if (!this.stompClient) {
      console.log('⚠️ STOMP client not initialized, creating new connection...');
      this.connect();
    }

    // Wait for connection to be established before subscribing
    const waitForConnection = () => {
      return new Promise<void>((resolve) => {
        if (this.connected && this.stompClient) {
          resolve();
        } else {
          const checkInterval = setInterval(() => {
            if (this.connected && this.stompClient) {
              clearInterval(checkInterval);
              resolve();
            }
          }, 100);
          
          // Timeout after 5 seconds
          setTimeout(() => {
            clearInterval(checkInterval);
            if (!this.connected) {
              console.error('❌ WebSocket connection timeout');
            }
            resolve();
          }, 5000);
        }
      });
    };

    waitForConnection().then(() => {
      if (this.stompClient && this.connected) {
        console.log('✅ Subscribing to /topic/student-activity');
        
        this.stompClient.subscribe('/topic/student-activity', (message) => {
          try {
            const activity: StudentActivity = JSON.parse(message.body);
            console.log('📨 Received student activity:', activity.activityType, activity);
            this.activitySubject.next(activity);
          } catch (error) {
            console.error('❌ Error parsing activity message:', error);
          }
        });
      } else {
        console.error('❌ Cannot subscribe - not connected to WebSocket');
      }
    });

    return this.activitySubject.asObservable();
  }

  /**
   * Subscribe to activities for a specific session
   */
  subscribeToSessionActivities(sessionId: number): Observable<StudentActivity> {
    if (!this.stompClient || !this.connected) {
      this.connect();
    }

    const sessionSubject = new Subject<StudentActivity>();

    if (this.stompClient && this.connected) {
      this.stompClient.subscribe(`/topic/session/${sessionId}/activity`, (message) => {
        const activity: StudentActivity = JSON.parse(message.body);
        console.log(`📨 Session ${sessionId} activity:`, activity);
        sessionSubject.next(activity);
      });
    }

    return sessionSubject.asObservable();
  }

  /**
   * Disconnect from WebSocket
   */
  disconnect(): void {
    if (this.stompClient) {
      this.stompClient.deactivate();
      this.connected = false;
      console.log('🔴 Student Activity WebSocket Disconnected');
    }
  }

  /**
   * Check if connected
   */
  isConnected(): boolean {
    return this.connected;
  }

  /**
   * Helper method to create activity objects
   */
  createActivity(
    type: string,
    severity: string,
    description: string,
    studentInfo?: { id: number; name: string; email?: string },
    sessionInfo?: { sessionId: number; testId: number; testName: string },
    metadata?: any
  ): StudentActivity {
    return {
      activityType: type,
      severity: severity,
      description: description,
      studentId: studentInfo?.id,
      studentName: studentInfo?.name,
      studentEmail: studentInfo?.email,
      sessionId: sessionInfo?.sessionId,
      testId: sessionInfo?.testId,
      testName: sessionInfo?.testName,
      metadata: metadata,
      timestamp: new Date().toISOString()
    };
  }
}
