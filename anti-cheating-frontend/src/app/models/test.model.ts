import { Exam } from './exam.model';

export interface Test {
  id?: number;
  title: string;
  description: string;
  createdBy?: string;
  createdAt?: string;
  duration: number; // in minutes
  numberOfQuestions?: number;
  exam?: Exam; // Reference to the parent exam
  examId?: number; // Exam ID
  type?: 'MCQ' | 'ESSAY';
}

export interface TestSubmission {
  [questionId: number]: string; // questionId: selectedOption
}
