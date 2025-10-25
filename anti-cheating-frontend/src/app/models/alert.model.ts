import { Student } from './student.model';
import { ExamSession } from './exam-session.model';

export type AlertSeverity = 'RED' | 'ORANGE' | 'YELLOW' | 'GREEN' | 'LOW' | 'MEDIUM' | 'HIGH' | 'CRITICAL';
export type AlertStatus = 'ACTIVE' | 'RESOLVED';

export interface Alert {
  id?: number;
  student?: Student;
  examSession?: ExamSession;
  severity: AlertSeverity;
  message: string;
  status: AlertStatus;
  timestamp?: string;
  createdAt?: string;
  resolvedAt?: string;
  resolved?: boolean;
}

export interface AlertCreate {
  studentId: number;
  examSessionId?: number;
  severity: AlertSeverity;
  message: string;
}
