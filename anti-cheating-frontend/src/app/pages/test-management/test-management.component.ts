import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { TestService } from '../../Services/test.service.service';
import { QuestionService } from '../../Services/question.service.service';
import { ExamService } from '../../Services/exam.service';
import { Test } from '../../models/test.model';
import { Question, QuestionCreate } from '../../models/question.model';
import { Exam } from '../../models/exam.model';

interface QuestionForm {
  type: 'MCQ' | 'ESSAY';
  text: string;
  options: [string, string, string, string];
  correctOption: number;
  topic: string;
}

@Component({
  selector: 'app-test-management',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './test-management.component.html',
  styleUrl: './test-management.component.css'
})
export class TestManagementComponent implements OnInit {
  // View states
  currentView: 'list' | 'create' | 'edit' | 'selectExisting' = 'list';
  
  // Tests
  tests: Test[] = [];
  selectedTest: Test | null = null;
  existingQuestions: Question[] = []; // Existing questions for edit mode
  
  // Selected tests for attaching to exam
  selectedTestsForExam: Set<number> = new Set();
  
  // Exams
  exams: Exam[] = [];
  
  // Test form
  testForm: Test = {
    title: '',
    description: '',
    duration: 60,
    examId: undefined,
    type: 'MCQ'
  };
  
  // Questions
  questions: QuestionForm[] = [];
  currentQuestion: QuestionForm = this.getEmptyQuestion();
  
  // Loading and error states
  loading = false;
  error = '';
  success = '';
  
  // Validation
  formErrors: string[] = [];
  
  // Coming from Exam Management
  comingFromExamId: number | null = null;
  selectedExamForTesting: Exam | null = null;

  constructor(
    private testService: TestService,
    private questionService: QuestionService,
    private examService: ExamService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    // Check if coming from exam management with examId
    this.route.queryParams.subscribe(params => {
      if (params['examId']) {
        this.comingFromExamId = parseInt(params['examId'], 10);
        // Show the list with quick actions to create or select tests
        this.currentView = 'list';
      }
    });
    
    this.loadTests();
    this.loadExams();
  }

  getEmptyQuestion(): QuestionForm {
    return {
      type: 'MCQ',
      text: '',
      options: ['', '', '', ''],
      correctOption: 0,
      topic: ''
    };
  }

  loadTests(): void {
    this.loading = true;
    this.error = '';
    console.log('🔄 Loading tests from database...');
    
    this.testService.getAllTests().subscribe({
      next: (tests) => {
        this.tests = tests || [];
        this.loading = false;
        
        console.log(`✅ Loaded ${this.tests.length} test(s)`);
        if (this.tests.length > 0) {
          console.log('📋 Test IDs:', this.tests.map(t => t.id).join(', '));
        } else {
          console.log('⚠️  No tests found in database');
        }
      },
      error: (error) => {
        console.error('❌ Error loading tests:', error);
        this.error = 'Failed to load tests';
        this.loading = false;
      }
    });
  }

  loadExams(): void {
    console.log('🔄 Loading exams for test creation...');
    this.examService.getAllExams().subscribe({
      next: (exams) => {
        this.exams = exams || [];
        console.log(`✅ Loaded ${this.exams.length} exam(s)`);
      },
      error: (error) => {
        console.error('❌ Error loading exams:', error);
        this.error = 'Failed to load exams. Please refresh the page.';
      }
    });
  }

  // View navigation
  showCreateTest(): void {
    this.currentView = 'create';
    this.testForm = {
      title: '',
      description: '',
      duration: 60,
      examId: undefined,
      type: 'MCQ'
    };
    this.questions = [];
    this.currentQuestion = this.getEmptyQuestion();
    this.formErrors = [];
    this.error = '';
    this.success = '';
  }

  showTestList(): void {
    this.currentView = 'list';
    this.selectedTest = null;
    this.existingQuestions = [];
    this.loadTests();
  }

  // Show existing tests for selection and attachment to exam
  showSelectExistingTests(): void {
    if (!this.comingFromExamId) {
      this.error = 'Cannot select tests: No exam selected';
      return;
    }
    this.currentView = 'selectExisting';
    this.selectedTestsForExam.clear();
    this.error = '';
    this.success = '';
  }

  // Toggle test selection for attachment to exam
  toggleTestSelection(testId: number | undefined): void {
    if (!testId) return;
    
    if (this.selectedTestsForExam.has(testId)) {
      this.selectedTestsForExam.delete(testId);
    } else {
      this.selectedTestsForExam.add(testId);
    }
  }

  // Check if a test is selected
  isTestSelected(testId: number | undefined): boolean {
    return testId ? this.selectedTestsForExam.has(testId) : false;
  }

  // Attach selected tests to exam
  attachSelectedTests(): void {
    if (!this.comingFromExamId) {
      this.error = 'Cannot attach tests: No exam selected';
      return;
    }

    if (this.selectedTestsForExam.size === 0) {
      this.error = 'Please select at least one test to attach';
      return;
    }

    this.loading = true;
    this.error = '';
    const testIds = Array.from(this.selectedTestsForExam);

    console.log(`📎 Attaching ${testIds.length} test(s) to exam ${this.comingFromExamId}`);

    this.testService.attachTestsToExam(this.comingFromExamId, testIds).subscribe({
      next: () => {
        this.success = `✅ Successfully attached ${testIds.length} test(s) to the exam!`;
        this.loading = false;
        this.selectedTestsForExam.clear();
        
        // Return to test list after 2 seconds
        setTimeout(() => {
          this.showTestList();
        }, 2000);
      },
      error: (error) => {
        console.error('❌ Error attaching tests:', error);
        this.error = 'Failed to attach tests to exam. Please try again.';
        this.loading = false;
      }
    });
  }

  // Edit existing test - add more questions
  editTest(test: Test): void {
    if (!test.id) {
      this.error = 'Cannot edit test: Invalid test ID';
      setTimeout(() => this.error = '', 3000);
      return;
    }

    this.selectedTest = test;
    this.testForm = { ...test };
    this.questions = [];
    this.currentQuestion = this.getEmptyQuestion();
    this.currentView = 'edit';
    this.error = '';
    this.success = '';
    this.formErrors = [];

    // Load existing questions
    this.loadExistingQuestions(test.id);
  }

  loadExistingQuestions(testId: number): void {
    this.loading = true;
    this.questionService.getQuestionsByTest(testId).subscribe({
      next: (questions: Question[]) => {
        this.existingQuestions = questions || [];
        this.loading = false;
      },
      error: (error: any) => {
        console.error('Error loading questions:', error);
        this.error = 'Failed to load existing questions';
        this.loading = false;
      }
    });
  }

  deleteExistingQuestion(questionId: number): void {
    if (!confirm('Are you sure you want to delete this question?')) {
      return;
    }

    this.questionService.deleteQuestion(questionId).subscribe({
      next: () => {
        this.existingQuestions = this.existingQuestions.filter(q => q.id !== questionId);
        this.success = 'Question deleted successfully';
        setTimeout(() => this.success = '', 3000);
      },
      error: (error: any) => {
        console.error('Error deleting question:', error);
        this.error = 'Failed to delete question';
      }
    });
  }

  saveNewQuestions(): void {
    if (this.questions.length === 0) {
      this.error = 'No new questions to add';
      return;
    }

    if (!this.selectedTest || !this.selectedTest.id) {
      this.error = 'Cannot add questions: Invalid test';
      return;
    }

    this.loading = true;
    this.error = '';

    this.saveQuestions(this.selectedTest.id);
  }

  // Question management
  addQuestion(): void {
    this.formErrors = [];
    
    // Validate question
    if (!this.currentQuestion.text.trim()) {
      this.formErrors.push('Question text is required');
    }
    
    if (this.currentQuestion.type === 'MCQ' && this.currentQuestion.options.some(opt => !opt.trim())) {
      this.formErrors.push('All four options must be filled');
    }
    
    if (!this.currentQuestion.topic.trim()) {
      this.formErrors.push('Topic is required');
    }
    
    if (this.formErrors.length > 0) {
      return;
    }
    
    // Add question to list
    this.questions.push({ ...this.currentQuestion });
    
    // Reset form
    this.currentQuestion = this.getEmptyQuestion();
    this.success = 'Question added successfully!';
    setTimeout(() => this.success = '', 3000);
  }

  onTestTypeChange(): void {
    if (this.testForm.type === 'ESSAY') {
      this.currentQuestion.type = 'ESSAY';
    } else {
      this.currentQuestion.type = 'MCQ';
    }
  }

  removeQuestion(index: number): void {
    if (confirm('Are you sure you want to remove this question?')) {
      this.questions.splice(index, 1);
    }
  }

  editQuestion(index: number): void {
    this.currentQuestion = { ...this.questions[index] };
    this.questions.splice(index, 1);
  }

  // Test submission
  validateTestForm(): boolean {
    this.formErrors = [];
    
    // Exam selection is now optional - tests can be created independently and attached to exams later
    
    if (!this.testForm.title.trim()) {
      this.formErrors.push('Test title is required');
    }
    
    if (!this.testForm.description.trim()) {
      this.formErrors.push('Test description is required');
    }
    
    if (this.testForm.duration <= 0) {
      this.formErrors.push('Duration must be greater than 0');
    }
    
    if (this.questions.length === 0) {
      this.formErrors.push('At least one question is required');
    }
    
    return this.formErrors.length === 0;
  }

  saveTest(): void {
    if (!this.validateTestForm()) {
      return;
    }
    
    this.loading = true;
    this.error = '';
    
    console.log('📤 Creating test with data:', {
      title: this.testForm.title,
      examId: this.testForm.examId,
      duration: this.testForm.duration
    });
    
    // First, create the test (independently without exam association)
    this.testService.createTest(this.testForm).subscribe({
      next: (createdTest) => {
        console.log('✅ Test created:', createdTest);
        
        // If we came from exam management, automatically attach the test to the exam
        if (this.comingFromExamId && createdTest.id) {
          this.testService.attachTestsToExam(this.comingFromExamId, [createdTest.id]).subscribe({
            next: () => {
              // Then, create all questions
              this.saveQuestions(createdTest.id!);
            },
            error: (error) => {
              console.error('Error attaching test to exam:', error);
              this.error = 'Test created but failed to attach to exam. You can attach it manually from the exam view.';
              // Still create questions even if attachment fails
              this.saveQuestions(createdTest.id!);
            }
          });
        } else {
          // Just create questions
          this.saveQuestions(createdTest.id!);
        }
      },
      error: (error) => {
        console.error('Error creating test:', error);
        this.error = 'Failed to create test';
        this.loading = false;
      }
    });
  }

  saveQuestions(testId: number): void {
    const questionPromises = this.questions.map(q => {
      const questionCreate: QuestionCreate = {
        test: { id: testId },
        text: q.text,
        options: q.type === 'ESSAY' ? [] : q.options,
        correctOption: q.type === 'ESSAY' ? 0 : q.correctOption,
        topic: q.topic,
        type: q.type
      };
      
      return this.questionService.createQuestion(questionCreate).toPromise();
    });
    
    Promise.all(questionPromises)
      .then(() => {
        this.success = 'Test created successfully with all questions!';
        this.loading = false;
        
        // Redirect to test list after 2 seconds
        setTimeout(() => {
          this.showTestList();
        }, 2000);
      })
      .catch((error) => {
        console.error('Error creating questions:', error);
        this.error = 'Test created but some questions failed to save';
        this.loading = false;
      });
  }

  // View test details
  viewTest(test: Test): void {
    if (!test.id) {
      this.error = 'Cannot view test: Invalid test ID';
      setTimeout(() => this.error = '', 3000);
      return;
    }
    this.router.navigate(['/admin/test-management', test.id]);
  }

  // Delete test
  deleteTest(testId: number | undefined): void {
    if (testId === undefined) {
      this.error = 'Cannot delete test: Invalid test ID';
      setTimeout(() => this.error = '', 3000);
      return;
    }
    
    const id: number = testId;
    const testExists = this.tests.find(t => t.id === id);
    if (!testExists) {
      console.warn(`⚠️  Test ID ${id} not found in current list`);
      this.error = `Test ID ${id} not found. The page may be showing outdated data.`;
      
      if (confirm('This test may have already been deleted or does not exist. Would you like to refresh the page to see current tests?')) {
        this.loadTests();
      }
      setTimeout(() => this.error = '', 5000);
      return;
    }
    
    console.log(`🗑️  Attempting to delete test ID ${id}: "${testExists.title}"`);
    
    if (!confirm(`Are you sure you want to delete test "${testExists.title}"? This action cannot be undone.`)) {
      return;
    }
    
    this.testService.deleteTest(id, false).subscribe({
      next: (response: any) => {
        console.log('✅ Test deleted successfully');
        this.success = 'Test deleted successfully';
        this.loadTests();
        setTimeout(() => this.success = '', 3000);
      },
      error: (err: any) => {
        console.error('❌ Error deleting test:', err);
        
        let errorMessage = 'Failed to delete test';
        if (err.error && err.error.error) {
          errorMessage = err.error.error;
          
          if (errorMessage.includes('Test not found')) {
            this.error = `Test ID ${id} does not exist. It may have been deleted already.`;
            setTimeout(() => {
              this.loadTests();
            }, 2000);
            setTimeout(() => this.error = '', 5000);
            return;
          }
          
          if (errorMessage.includes('student') || errorMessage.includes('taken') || errorMessage.includes('results')) {
            const forceDelete = confirm(
              '⚠️ WARNING: ' + errorMessage + '\n\n' +
              '🗑️ Do you want to FORCE DELETE this test?\n\n' +
              '⚠️ THIS CANNOT BE UNDONE!\n\n' +
              'Click OK to force delete, or Cancel to keep the test.'
            );
            
            if (forceDelete) {
              this.forceDeleteTest(id);
              return;
            } else {
              this.error = 'Delete cancelled. Test has been kept.';
              setTimeout(() => this.error = '', 3000);
              return;
            }
          }
        } else if (err.error && err.error.message) {
          errorMessage = err.error.message;
        } else if (err.message) {
          errorMessage = err.message;
        }
        
        this.error = errorMessage;
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  // Force delete test (with student results)
  forceDeleteTest(testId: number): void {
    console.log('🗑️💪 Force deleting test ID:', testId);
    this.testService.deleteTest(testId, true).subscribe({
      next: (response) => {
        console.log('✅ Force delete successful:', response);
        this.success = '✅ Test and all student results deleted successfully';
        if (response.warning) {
          console.warn('⚠️ Warning:', response.warning);
        }
        this.loadTests();
        setTimeout(() => this.success = '', 5000);
      },
      error: (error: any) => {
        console.error('❌ Error force deleting test:', error);
        let errorMessage = 'Failed to force delete test';
        if (error.error && error.error.error) {
          errorMessage = error.error.error;
        } else if (error.error && error.error.message) {
          errorMessage = error.error.message;
        } else if (error.message) {
          errorMessage = error.message;
        }
        console.error('💥 Force delete failed:', errorMessage);
        this.error = errorMessage;
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  // Navigation
  goBack(): void {
    this.router.navigate(['/admin/dashboard']);
  }
}
