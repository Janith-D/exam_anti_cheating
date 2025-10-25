export interface Exam {
  id?: number;
  title: string;
  description: string;
  startDate: string;
  endDate: string;
  createdBy?: string;
  createdAt?: string;
  status: ExamStatus;
  maxStudents?: number;
  passingScore?: number;
  tests?: any[]; // List of tests in the exam
  enrollments?: any[]; // List of enrollments
}

export enum ExamStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  ONGOING = 'ONGOING',
  COMPLETED = 'COMPLETED',
  ARCHIVED = 'ARCHIVED'
}

export interface Enrollment {
  id?: number;
  student?: any;
  exam?: Exam;
  faceEmbedding?: string;
  enrollmentDate?: string;
  isVerified?: boolean;
  verificationScore?: number;
  status: EnrollmentStatus;
}

export enum EnrollmentStatus {
  PENDING = 'PENDING',
  VERIFIED = 'VERIFIED',
  APPROVED = 'APPROVED',
  REJECTED = 'REJECTED'
}

export interface EnrollmentRequest {
  studentId: number;
  examId: number;
  image: File;
}

export interface EnrollmentResponse {
  message: string;
  enrollmentId: number;
  examId: number;
  status: EnrollmentStatus;
  verificationScore?: number;
}
