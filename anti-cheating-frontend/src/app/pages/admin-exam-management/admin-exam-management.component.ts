import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { Router, RouterModule } from '@angular/router';
import { FormsModule } from '@angular/forms';
import { ExamService } from '../../Services/exam.service';
import { EnrollmentService } from '../../Services/enrollment.service';
import { TestService } from '../../Services/test.service.service';
import { QuestionService } from '../../Services/question.service.service';
import { AuthService } from '../../Services/auth.service.service';
import { Exam, ExamStatus, Enrollment, EnrollmentStatus } from '../../models/exam.model';
import { Test } from '../../models/test.model';
import { QuestionCreate } from '../../models/question.model';

interface QuestionForm {
  text: string;
  options: [string, string, string, string];
  correctOption: number;
  topic: string;
}

interface TestForm {
  title: string;
  description: string;
  duration: number;
  questions: QuestionForm[];
  passingScore?: number;
  totalMarks?: number;
  testOrder?: number;
}

@Component({
  selector: 'app-admin-exam-management',
  standalone: true,
  imports: [CommonModule, RouterModule, FormsModule],
  templateUrl: './admin-exam-management.component.html',
  styleUrl: './admin-exam-management.component.css'
})
export class AdminExamManagementComponent implements OnInit {
  exams: Exam[] = [];
  loading = false;
  errorMessage = '';
  successMessage = '';
  
  // Create/Edit Exam Modal
  showExamModal = false;
  createMode: 'simple' | 'detailed' = 'simple'; // 'simple' or 'detailed' with tests
  editingExam: Exam | null = null;
  examForm: Exam = this.getEmptyExam();
  
  // Detailed Exam Creation with Tests
  tests: TestForm[] = [];
  currentTest: TestForm = this.getEmptyTest();
  currentQuestion: QuestionForm = this.getEmptyQuestion();
  
  // Enrollment Management Modal
  showEnrollmentModal = false;
  selectedExam: Exam | null = null;
  enrollments: Enrollment[] = [];
  
  ExamStatus = ExamStatus;
  EnrollmentStatus = EnrollmentStatus;

  constructor(
    private examService: ExamService,
    private enrollmentService: EnrollmentService,
    private testService: TestService,
    private questionService: QuestionService,
    private authService: AuthService,
    private router: Router
  ) {}

  ngOnInit(): void {
    if (!this.authService.isAdmin) {
      this.router.navigate(['/login']);
      return;
    }
    this.loadExams();
  }

  getEmptyExam(): Exam {
    return {
      title: '',
      description: '',
      startDate: '',
      endDate: '',
      status: ExamStatus.DRAFT,
      maxStudents: undefined,
      passingScore: 70
    };
  }

  getEmptyTest(): TestForm {
    return {
      title: '',
      description: '',
      duration: 60,
      questions: [],
      passingScore: 60,
      totalMarks: 100,
      testOrder: 1
    };
  }

  getEmptyQuestion(): QuestionForm {
    return {
      text: '',
      options: ['', '', '', ''],
      correctOption: 0,
      topic: ''
    };
  }

  loadExams(): void {
    this.loading = true;
    this.errorMessage = '';
    
    this.examService.getAllExams().subscribe({
      next: (exams) => {
        this.exams = exams;
        this.loading = false;
      },
      error: (error) => {
        console.error('Error loading exams:', error);
        this.errorMessage = 'Failed to load exams';
        this.loading = false;
      }
    });
  }

  openCreateModal(): void {
    this.editingExam = null;
    this.examForm = this.getEmptyExam();
    this.createMode = 'simple';
    this.tests = [];
    this.currentTest = this.getEmptyTest();
    this.currentQuestion = this.getEmptyQuestion();
    this.showExamModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  switchToDetailedMode(): void {
    this.createMode = 'detailed';
    if (this.tests.length === 0) {
      this.tests.push(this.getEmptyTest());
    }
  }

  switchToSimpleMode(): void {
    if (this.tests.length > 0 && !confirm('Switching to simple mode will discard all tests. Continue?')) {
      return;
    }
    this.createMode = 'simple';
    this.tests = [];
    this.currentTest = this.getEmptyTest();
    this.currentQuestion = this.getEmptyQuestion();
  }

  openEditModal(exam: Exam): void {
    this.editingExam = exam;
    this.examForm = { ...exam };
    this.showExamModal = true;
    this.errorMessage = '';
    this.successMessage = '';
  }

  closeExamModal(): void {
    this.showExamModal = false;
    this.editingExam = null;
    this.examForm = this.getEmptyExam();
    this.createMode = 'simple';
    this.tests = [];
    this.currentTest = this.getEmptyTest();
    this.currentQuestion = this.getEmptyQuestion();
  }

  // ==================== Test Management ====================
  
  addTest(): void {
    if (!this.currentTest.title.trim()) {
      this.errorMessage = 'Test title is required';
      return;
    }
    if (!this.currentTest.duration || this.currentTest.duration <= 0) {
      this.errorMessage = 'Test duration must be greater than 0';
      return;
    }
    if (this.currentTest.questions.length === 0) {
      this.errorMessage = 'Test must have at least one question';
      return;
    }
    
    // Ensure numeric values are valid
    const testToAdd: TestForm = {
      ...this.currentTest,
      duration: Number(this.currentTest.duration),
      passingScore: Number(this.currentTest.passingScore) || 60,
      totalMarks: Number(this.currentTest.totalMarks) || 100,
      testOrder: Number(this.currentTest.testOrder) || this.tests.length + 1
    };
    
    this.tests.push(testToAdd);
    this.currentTest = this.getEmptyTest();
    this.currentTest.testOrder = this.tests.length + 1;
    this.successMessage = `✅ Test added! Total: ${this.tests.length} test(s)`;
    setTimeout(() => this.successMessage = '', 2000);
  }

  removeTest(index: number): void {
    if (confirm('Remove this test and all its questions?')) {
      this.tests.splice(index, 1);
      // Update test orders
      this.tests.forEach((test, i) => test.testOrder = i + 1);
    }
  }

  editTest(index: number): void {
    this.currentTest = { ...this.tests[index] };
    this.tests.splice(index, 1);
  }

  // ==================== Question Management ====================
  
  addQuestionToCurrentTest(): void {
    if (!this.currentQuestion.text.trim()) {
      this.errorMessage = 'Question text is required';
      return;
    }
    
    if (this.currentQuestion.options.some(opt => !opt.trim())) {
      this.errorMessage = 'All options must be filled';
      return;
    }
    
    this.currentTest.questions.push({ ...this.currentQuestion });
    this.currentQuestion = this.getEmptyQuestion();
    this.successMessage = `Question added! Total: ${this.currentTest.questions.length} question(s)`;
    setTimeout(() => this.successMessage = '', 2000);
  }

  removeQuestionFromTest(index: number): void {
    if (confirm('Remove this question?')) {
      this.currentTest.questions.splice(index, 1);
    }
  }

  editQuestionInTest(index: number): void {
    this.currentQuestion = { ...this.currentTest.questions[index] };
    this.currentTest.questions.splice(index, 1);
  }

  saveExam(): void {
    if (!this.validateExam()) return;

    this.loading = true;
    
    if (this.editingExam) {
      // Update existing exam (simple mode only)
      this.examService.updateExam(this.editingExam.id!, this.examForm).subscribe({
        next: (exam) => {
          this.successMessage = 'Exam updated successfully!';
          this.loadExams();
          setTimeout(() => this.closeExamModal(), 1500);
        },
        error: (error) => {
          console.error('Error updating exam:', error);
          this.errorMessage = 'Failed to update exam';
          this.loading = false;
        }
      });
    } else {
      // Create new exam
      if (this.createMode === 'simple') {
        this.createSimpleExam();
      } else {
        this.createDetailedExam();
      }
    }
  }

  createSimpleExam(): void {
    this.examService.createExam(this.examForm).subscribe({
      next: (exam) => {
        this.successMessage = '✅ Exam created successfully! IMPORTANT: Click the 📤 Publish button to make it visible to students.';
        this.loadExams();
        setTimeout(() => {
          this.closeExamModal();
        }, 2000);
      },
      error: (error) => {
        console.error('Error creating exam:', error);
        this.errorMessage = 'Failed to create exam';
        this.loading = false;
      }
    });
  }

  createDetailedExam(): void {
    // Step 1: Create the exam
    this.examService.createExam(this.examForm).subscribe({
      next: (response) => {
        console.log('✅ Exam created:', response);
        this.successMessage = 'Exam created! Creating tests...';
        
        // Step 2: Create all tests with questions
        // Backend returns examId in the response object
        this.createAllTests(response.examId);
      },
      error: (error) => {
        console.error('❌ Error creating exam:', error);
        this.errorMessage = 'Failed to create exam';
        this.loading = false;
      }
    });
  }

  async createAllTests(examId: number): Promise<void> {
    try {
      console.log('🔧 Starting test creation process...');
      console.log('  Number of tests to create:', this.tests.length);
      console.log('  Exam ID (parameter):', examId);
      console.log('  Exam ID type:', typeof examId);
      console.log('  Exam ID is null?', examId === null);
      console.log('  Exam ID is undefined?', examId === undefined);
      console.log('  Current user:', this.authService.currentUserValue);
      console.log('  JWT token exists:', !!this.authService.getToken());
      console.log('  JWT token preview:', this.authService.getToken()?.substring(0, 30) + '...');
      
      for (let i = 0; i < this.tests.length; i++) {
        const testForm = this.tests[i];
        console.log(`\n📝 Processing test ${i + 1}/${this.tests.length}:`, testForm.title);
        this.successMessage = `Creating test ${i + 1} of ${this.tests.length}...`;
        
        // Validate test data before sending
        if (!testForm.title || testForm.title.trim() === '') {
          throw new Error(`Test ${i + 1}: Title is required`);
        }
        if (!testForm.duration || testForm.duration <= 0) {
          throw new Error(`Test ${i + 1}: Duration must be greater than 0`);
        }
        
        // Create test with validated data
        const testData: any = {
          title: testForm.title.trim(),
          description: testForm.description?.trim() || '',
          duration: Number(testForm.duration), // Ensure it's a number
          examId: examId, // Send just the ID, not the full exam object
          testOrder: Number(testForm.testOrder) || 1,
          passingScore: Number(testForm.passingScore) || 60,
          totalMarks: Number(testForm.totalMarks) || 100
        };
        
        console.log(`📤 Sending test ${i + 1} data:`, testData);
        
        const createdTest = await this.testService.createTest(testData).toPromise();
        console.log(`✅ Test ${i + 1} created:`, createdTest);
        
        // Create questions for this test
        if (createdTest && createdTest.id && testForm.questions.length > 0) {
          await this.createQuestionsForTest(createdTest.id, testForm.questions);
        }
      }
      
      // All done!
      this.loading = false;
      this.successMessage = `✅ Exam created with ${this.tests.length} test(s) and all questions! Click 📤 Publish to make it visible.`;
      this.loadExams();
      
      setTimeout(() => {
        this.closeExamModal();
      }, 3000);
      
    } catch (error: any) {
      console.error('❌ Error creating tests:', error);
      console.error('  Error status:', error?.status);
      console.error('  Error statusText:', error?.statusText);
      console.error('  Error message:', error?.message);
      console.error('  Error details:', error?.error);
      console.error('  Full error object:', error);
      
      // Check if it's an authentication/authorization issue
      if (error?.status === 401) {
        console.error('🚨 401 UNAUTHORIZED ERROR!');
        console.error('  This means the JWT token is invalid or missing admin role');
        console.error('  Current user from localStorage:', localStorage.getItem('currentUser'));
        console.error('  Current token from localStorage:', localStorage.getItem('token')?.substring(0, 30) + '...');
      }
      
      // Extract detailed error message
      let errorMsg = 'Exam created but some tests/questions failed. ';
      if (error?.status === 401) {
        errorMsg = '🔐 Authentication Error: Your admin session may have expired or you don\'t have admin permissions. ';
        errorMsg += 'Please logout and login again as admin. ';
      } else if (error?.error?.message) {
        errorMsg += error.error.message;
      } else if (error?.message) {
        errorMsg += error.message;
      } else if (error?.status === 400) {
        errorMsg += 'Invalid test data. Please check test title and duration.';
      }
      
      this.errorMessage = errorMsg;
      this.loading = false;
    }
  }

  async createQuestionsForTest(testId: number, questions: QuestionForm[]): Promise<void> {
    console.log(`📋 Creating ${questions.length} question(s) for test ${testId}...`);
    console.log('   Questions data:', questions);
    
    if (!questions || questions.length === 0) {
      console.warn('⚠️ No questions to create for test', testId);
      return;
    }
    
    // Create questions one by one to avoid overwhelming the server
    // and to get better error reporting
    const createdQuestions: any[] = [];
    const failedQuestions: any[] = [];
    
    for (let i = 0; i < questions.length; i++) {
      const q = questions[i];
      const questionCreate: QuestionCreate = {
        test: { id: testId },
        text: q.text,
        options: q.options,
        correctOption: q.correctOption,
        topic: q.topic || 'General'
      };
      
      console.log(`   📝 Creating question ${i + 1}/${questions.length}:`, questionCreate);
      
      try {
        const result = await this.questionService.createQuestion(questionCreate).toPromise();
        createdQuestions.push(result);
        console.log(`   ✅ Question ${i + 1} created with ID: ${result?.id}`);
      } catch (error: any) {
        console.error(`   ❌ Question ${i + 1} FAILED:`, error);
        console.error(`      Status: ${error?.status}`);
        console.error(`      Message: ${error?.message}`);
        console.error(`      Error details:`, error?.error);
        
        failedQuestions.push({
          index: i + 1,
          question: questionCreate,
          error: error
        });
        
        // Check if it's a 401 error
        if (error?.status === 401) {
          console.error('   🚨 401 ERROR: Authentication failed for question creation!');
          console.error('      This means your JWT token is invalid or missing admin role');
          throw new Error('Authentication failed: Please logout and login again as admin');
        }
      }
    }
    
    // Report results
    if (createdQuestions.length > 0) {
      console.log(`✅ Successfully created ${createdQuestions.length}/${questions.length} question(s) for test ${testId}`);
    }
    
    if (failedQuestions.length > 0) {
      console.error(`❌ Failed to create ${failedQuestions.length}/${questions.length} question(s)`);
      console.error('   Failed questions:', failedQuestions);
      throw new Error(`${failedQuestions.length} question(s) failed to save. Check console for details.`);
    }
  }

  validateExam(): boolean {
    if (!this.examForm.title || !this.examForm.description) {
      this.errorMessage = 'Title and description are required';
      return false;
    }
    
    if (!this.examForm.startDate || !this.examForm.endDate) {
      this.errorMessage = 'Start and end dates are required';
      return false;
    }
    
    if (new Date(this.examForm.startDate) >= new Date(this.examForm.endDate)) {
      this.errorMessage = 'End date must be after start date';
      return false;
    }
    
    // Validation for detailed mode
    if (this.createMode === 'detailed' && this.tests.length === 0) {
      this.errorMessage = 'Please add at least one test or switch to simple mode';
      return false;
    }
    
    return true;
  }

  deleteExam(exam: Exam): void {
    if (!confirm(`Are you sure you want to delete "${exam.title}"?`)) {
      return;
    }

    this.examService.deleteExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam deleted successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error deleting exam:', error);
        this.errorMessage = 'Failed to delete exam';
      }
    });
  }

  publishExam(exam: Exam): void {
    if (!confirm(`Publish "${exam.title}"?\n\nThis will make the exam visible to students for enrollment.`)) {
      return;
    }
    
    this.examService.publishExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = `✅ SUCCESS! Exam "${exam.title}" is now PUBLISHED and visible to students. They can now enroll!`;
        this.loadExams();
        setTimeout(() => this.successMessage = '', 6000);
      },
      error: (error) => {
        console.error('Error publishing exam:', error);
        this.errorMessage = 'Failed to publish exam';
      }
    });
  }

  startExam(exam: Exam): void {
    this.examService.startExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam started successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error starting exam:', error);
        this.errorMessage = 'Failed to start exam';
      }
    });
  }

  completeExam(exam: Exam): void {
    if (!confirm(`Are you sure you want to complete "${exam.title}"?`)) {
      return;
    }

    this.examService.completeExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam completed successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error completing exam:', error);
        this.errorMessage = 'Failed to complete exam';
      }
    });
  }

  archiveExam(exam: Exam): void {
    this.examService.archiveExam(exam.id!).subscribe({
      next: () => {
        this.successMessage = 'Exam archived successfully!';
        this.loadExams();
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error archiving exam:', error);
        this.errorMessage = 'Failed to archive exam';
      }
    });
  }

  openEnrollmentModal(exam: Exam): void {
    this.selectedExam = exam;
    this.showEnrollmentModal = true;
    this.loadEnrollments(exam.id!);
  }

  closeEnrollmentModal(): void {
    this.showEnrollmentModal = false;
    this.selectedExam = null;
    this.enrollments = [];
  }

  loadEnrollments(examId: number): void {
    this.enrollmentService.getExamEnrollments(examId).subscribe({
      next: (enrollments) => {
        this.enrollments = enrollments;
      },
      error: (error) => {
        console.error('Error loading enrollments:', error);
        this.errorMessage = 'Failed to load enrollments';
      }
    });
  }

  updateEnrollmentStatus(enrollment: Enrollment, status: EnrollmentStatus): void {
    this.enrollmentService.updateEnrollmentStatus(enrollment.id!, status).subscribe({
      next: () => {
        this.successMessage = `Enrollment ${status.toLowerCase()} successfully!`;
        if (this.selectedExam) {
          this.loadEnrollments(this.selectedExam.id!);
        }
        setTimeout(() => this.successMessage = '', 3000);
      },
      error: (error) => {
        console.error('Error updating enrollment:', error);
        this.errorMessage = 'Failed to update enrollment status';
      }
    });
  }

  getStatusColor(status: ExamStatus): string {
    return this.examService.getStatusColor(status);
  }

  getEnrollmentStatusColor(status: EnrollmentStatus): string {
    return this.enrollmentService.getStatusColor(status);
  }

  getEnrollmentStatusText(status: EnrollmentStatus): string {
    return this.enrollmentService.getStatusText(status);
  }

  formatDate(dateString: string): string {
    return new Date(dateString).toLocaleString();
  }

  logout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
