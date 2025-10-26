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
    if (this.currentTest.questions.length === 0) {
      this.errorMessage = 'Test must have at least one question';
      return;
    }
    
    this.tests.push({ ...this.currentTest });
    this.currentTest = this.getEmptyTest();
    this.currentTest.testOrder = this.tests.length + 1;
    this.successMessage = `Test added! Total: ${this.tests.length} test(s)`;
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
      next: (createdExam) => {
        console.log('✅ Exam created:', createdExam);
        this.successMessage = 'Exam created! Creating tests...';
        
        // Step 2: Create all tests with questions
        this.createAllTests(createdExam.id!);
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
      for (let i = 0; i < this.tests.length; i++) {
        const testForm = this.tests[i];
        this.successMessage = `Creating test ${i + 1} of ${this.tests.length}...`;
        
        // Create test
        const testData: any = {
          title: testForm.title,
          description: testForm.description,
          duration: testForm.duration,
          exam: { id: examId } as Exam,
          testOrder: testForm.testOrder,
          passingScore: testForm.passingScore,
          totalMarks: testForm.totalMarks
        };
        
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
      
    } catch (error) {
      console.error('❌ Error creating tests:', error);
      this.errorMessage = 'Exam created but some tests/questions failed';
      this.loading = false;
    }
  }

  async createQuestionsForTest(testId: number, questions: QuestionForm[]): Promise<void> {
    const questionPromises = questions.map(q => {
      const questionCreate: QuestionCreate = {
        test: { id: testId },
        text: q.text,
        options: q.options,
        correctOption: q.correctOption,
        topic: q.topic
      };
      return this.questionService.createQuestion(questionCreate).toPromise();
    });
    
    await Promise.all(questionPromises);
    console.log(`✅ ${questions.length} question(s) created for test ${testId}`);
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
