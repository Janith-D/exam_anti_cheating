import { Student } from './student.model';
import { ExamSession } from './exam-session.model';

export type EventType = 
  | 'COPY' 
  | 'PASTE' 
  | 'TAB_SWITCH' 
  | 'SCREENSHOT' 
  | 'BLUR' 
  | 'FOCUS'
  | 'CONTEXT_MENU'
  | 'CHEATING_TAB_SWITCH';

export interface Event {
  id?: number;
  student?: Student;
  examSession?: ExamSession;
  type: EventType;
  details: string;
  timestamp?: string;
  ipAddress?: string;
}

export interface EventLog {
  studentId: number;
  examSessionId?: number;
  type: EventType;
  details: string;
}
