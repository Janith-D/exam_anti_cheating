import { Test } from './test.model';

export interface Question {
  id?: number;
  test?: Test;
  text: string;
  options: string[]; // Array of 4 options
  correctOption?: number; // Index 0-3, not exposed to students
  topic: string;
}

export interface QuestionCreate {
  test: {
    id: number;
  };
  text: string;
  options: string[];
  correctOption: number;
  topic: string;
}
