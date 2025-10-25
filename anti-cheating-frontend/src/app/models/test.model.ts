export interface Test {
  id?: number;
  title: string;
  description: string;
  createdBy?: string;
  createdAt?: string;
  duration: number; // in minutes
  numberOfQuestions?: number;
}

export interface TestSubmission {
  [questionId: number]: number; // questionId: selectedOption
}
