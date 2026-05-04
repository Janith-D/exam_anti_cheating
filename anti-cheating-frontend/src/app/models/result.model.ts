import { Student } from './student.model';
import { Test } from './test.model';

export type ResultStatus = 'GRADED' | 'PENDING_REVIEW';

export interface TestResult {
  id?: number;
  student?: Student;
  test?: Test;
  correctAnswers: number;
  totalQuestions: number;
  scorePercentage: number;
  completedAt?: string;
  status?: ResultStatus;
  essayAnswersJson?: string; // JSON string: { [questionId]: answerText }
}
