import { Test } from './test.model';

export interface ExamSession {
  id?: number;
  test?: Test;
  startTime: string;
  endTime?: string;
}
