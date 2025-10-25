import { Injectable } from '@angular/core';
import { Client, IMessage } from '@stomp/stompjs';
import { BehaviorSubject, Observable } from 'rxjs';
import { Alert } from '../models/alert.model';
import SockJS from 'sockjs-client';

@Injectable({
  providedIn: 'root'
})
export class WebSocketService {
  private client: Client;
  private alertSubject: BehaviorSubject<Alert | null> = new BehaviorSubject<Alert | null>(null);
  public alerts$: Observable<Alert | null> = this.alertSubject.asObservable();

  constructor() {
    this.client = new Client({
      webSocketFactory: () => new SockJS('http://localhost:8080/ws'),
      reconnectDelay: 5000,
      heartbeatIncoming: 4000,
      heartbeatOutgoing: 4000,
    });

    this.client.onConnect = () => {
      console.log('🟢 WebSocket Connected');
      
      // Subscribe to alerts topic
      this.client.subscribe('/topic/alerts', (message: IMessage) => {
        const alert: Alert = JSON.parse(message.body);
        console.log('🚨 New Alert Received:', alert);
        this.alertSubject.next(alert);
      });
    };

    this.client.onStompError = (frame) => {
      console.error('🔴 WebSocket Error:', frame.headers['message']);
      console.error('Details:', frame.body);
    };
  }

  connect(): void {
    if (!this.client.active) {
      this.client.activate();
    }
  }

  disconnect(): void {
    if (this.client.active) {
      this.client.deactivate();
    }
  }

  isConnected(): boolean {
    return this.client.active;
  }

  getAlerts(): Observable<Alert | null> {
    return this.alerts$;
  }
}
