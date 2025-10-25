import { Test } from './test.model';
import { Exam } from './exam.model';

export interface ExamSession {
  id?: number;
  examName?: string;
  exam?: Exam; // NEW: Direct relationship with Exam
  test?: Test; // Keep for backward compatibility
  startTime: string;
  endTime?: string;
  durationMinutes?: number;
  status?: string;
  createdBy?: string;
}
