import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';
import { TestService } from '../../Services/test.service.service';
import { QuestionService } from '../../Services/question.service.service';
import { Test } from '../../models/test.model';
import { Question, QuestionCreate } from '../../models/question.model';

interface QuestionForm {
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
  currentView: 'list' | 'create' | 'edit' = 'list';
  
  // Tests
  tests: Test[] = [];
  selectedTest: Test | null = null;
  existingQuestions: Question[] = []; // Existing questions for edit mode
  
  // Test form
  testForm: Test = {
    title: '',
    description: '',
    duration: 60
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

  constructor(
    private testService: TestService,
    private questionService: QuestionService,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadTests();
  }

  getEmptyQuestion(): QuestionForm {
    return {
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

  // View navigation
  showCreateTest(): void {
    this.currentView = 'create';
    this.testForm = {
      title: '',
      description: '',
      duration: 60
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
    
    if (this.currentQuestion.options.some(opt => !opt.trim())) {
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
    
    // First, create the test
    this.testService.createTest(this.testForm).subscribe({
      next: (createdTest) => {
        console.log('Test created:', createdTest);
        
        // Then, create all questions
        this.saveQuestions(createdTest.id!);
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
        options: q.options,
        correctOption: q.correctOption,
        topic: q.topic
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
    if (!testId) {
      this.error = 'Cannot delete test: Invalid test ID';
      setTimeout(() => this.error = '', 3000);
      return;
    }
    
    // Check if test exists in current list
    const testExists = this.tests.find(t => t.id === testId);
    if (!testExists) {
      console.warn(`⚠️  Test ID ${testId} not found in current list`);
      this.error = `Test ID ${testId} not found. The page may be showing outdated data.`;
      
      // Offer to refresh
      if (confirm('This test may have already been deleted or does not exist. Would you like to refresh the page to see current tests?')) {
        this.loadTests();
      }
      setTimeout(() => this.error = '', 5000);
      return;
    }
    
    console.log(`🗑️  Attempting to delete test ID ${testId}: "${testExists.title}"`);
    
    if (!confirm(`Are you sure you want to delete test "${testExists.title}"? This action cannot be undone.`)) {
      return;
    }
    
    // First try normal delete
    this.testService.deleteTest(testId, false).subscribe({
      next: (response) => {
        console.log('✅ Test deleted successfully');
        this.success = 'Test deleted successfully';
        this.loadTests();
        setTimeout(() => this.success = '', 3000);
      },
      error: (error: any) => {
        console.error('❌ Error deleting test:', error);
        console.error('Error details:', {
          status: error.status,
          statusText: error.statusText,
          errorBody: error.error
        });
        
        // Extract error message from backend
        let errorMessage = 'Failed to delete test';
        if (error.error && error.error.error) {
          errorMessage = error.error.error;
          
          // Check for specific error types
          if (errorMessage.includes('Test not found')) {
            console.warn('⚠️  Test no longer exists in database');
            this.error = `Test ID ${testId} does not exist. It may have been deleted already.`;
            
            // Auto-refresh to show current state
            setTimeout(() => {
              console.log('🔄 Auto-refreshing test list...');
              this.loadTests();
            }, 2000);
            
            setTimeout(() => this.error = '', 5000);
            return;
          }
          
          // Check if error is about student results
          if (errorMessage.includes('student(s) have already taken this test')) {
            // Ask for force delete confirmation
            const forceDelete = confirm(
              '⚠️ WARNING: ' + errorMessage + '\n\n' +
              '🗑️ Do you want to FORCE DELETE this test?\n\n' +
              '❌ This will permanently delete:\n' +
              '   • The test\n' +
              '   • All student results\n' +
              '   • All questions\n\n' +
              '⚠️ THIS CANNOT BE UNDONE!\n\n' +
              'Click OK to force delete, or Cancel to keep the test.'
            );
            
            if (forceDelete) {
              this.forceDeleteTest(testId);
              return;
            }
          }
        } else if (error.message) {
          errorMessage = error.message;
        }
        
        this.error = errorMessage;
        setTimeout(() => this.error = '', 5000);
      }
    });
  }

  // Force delete test (with student results)
  forceDeleteTest(testId: number): void {
    this.testService.deleteTest(testId, true).subscribe({
      next: (response) => {
        this.success = '✅ Test and all student results deleted successfully';
        if (response.warning) {
          console.warn(response.warning);
        }
        this.loadTests();
        setTimeout(() => this.success = '', 5000);
      },
      error: (error: any) => {
        console.error('Error force deleting test:', error);
        let errorMessage = 'Failed to force delete test';
        if (error.error && error.error.error) {
          errorMessage = error.error.error;
        }
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
