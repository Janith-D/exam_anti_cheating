import { Student } from './student.model';
import { Test } from './test.model';

export interface TestResult {
  id?: number;
  student?: Student;
  test?: Test;
  correctAnswers: number;
  totalQuestions: number;
  scorePercentage: number;
  completedAt?: string;
}
