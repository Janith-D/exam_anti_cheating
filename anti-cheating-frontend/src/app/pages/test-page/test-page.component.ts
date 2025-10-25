import { Component, OnInit, OnDestroy } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { interval, Subscription } from 'rxjs';
import { TestService } from '../../Services/test.service.service';
import { QuestionService } from '../../Services/question.service.service';
import { EventService } from '../../Services/event.service.service';
import { ExamSessionService } from '../../Services/exam-session.service';
import { AuthService } from '../../Services/auth.service.service';
import { StudentActivityService } from '../../Services/student-activity.service';
import { Test } from '../../models/test.model';
import { Question } from '../../models/question.model';

@Component({
  selector: 'app-test-page',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './test-page.component.html',
  styleUrl: './test-page.component.css'
})
export class TestPageComponent implements OnInit, OnDestroy {
  test: Test | null = null;
  questions: Question[] = [];
  currentQuestionIndex = 0;
  answers: { [questionId: number]: number } = {}; // Changed from string to number
  
  // Timer
  timeRemaining = 0; // in seconds
  timerSubscription: Subscription | null = null;
  
  // Loading states
  loading = true;
  submitting = false;
  errorMessage = '';
  
  // User info
  currentUser: any = null;
  testId: number = 0;
  sessionId: number = 0;
  
  // Monitoring
  tabSwitchCount = 0;
  suspiciousActivityDetected = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private testService: TestService,
    private questionService: QuestionService,
    private eventService: EventService,
    private examSessionService: ExamSessionService,
    private authService: AuthService,
    private studentActivityService: StudentActivityService
  ) {}

  ngOnInit(): void {
    this.currentUser = this.authService.currentUserValue;
    
    // Get test ID from route
    this.route.params.subscribe(params => {
      this.testId = +params['id'];
      this.loadTestAndQuestions();
    });
    
    // Monitor visibility changes (tab switches)
    this.setupVisibilityMonitoring();
    
    // Monitor copy/paste attempts
    this.setupCopyPasteMonitoring();
    
    // Warn before leaving page
    this.setupBeforeUnloadHandler();
  }

  ngOnDestroy(): void {
    if (this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
    
    // Disconnect WebSocket when component is destroyed
    this.studentActivityService.disconnect();
  }

  loadTestAndQuestions(): void {
    this.loading = true;
    this.errorMessage = '';
    
    // Load test details
    this.testService.getTestById(this.testId).subscribe({
      next: (test) => {
        this.test = test;
        this.timeRemaining = test.duration * 60; // Convert minutes to seconds
        this.startTimer();
        
        // Load questions for this test
        this.loadQuestions();
      },
      error: (error) => {
        console.error('Error loading test:', error);
        this.errorMessage = 'Failed to load test. Please try again.';
        this.loading = false;
      }
    });
  }

  loadQuestions(): void {
    this.questionService.getQuestionsByTest(this.testId).subscribe({
      next: (questions) => {
        this.questions = questions;
        this.loading = false;
        
        // Get or create exam session for this test
        this.examSessionService.getOrCreateSessionForTest(this.testId).subscribe({
          next: (session) => {
            this.sessionId = session.id || 0;
            console.log('✅ Exam session created/retrieved:', this.sessionId);
            
            // Connect to WebSocket for real-time monitoring
            this.studentActivityService.connect();
            
            // Send TEST_STARTED activity via WebSocket for real-time admin monitoring
            const testStartedActivity = this.studentActivityService.createActivity(
              'TEST_STARTED',
              'LOW',
              `Started test: ${this.test?.title}`,
              { 
                id: this.currentUser.id, 
                name: this.currentUser.username,
                email: this.currentUser.email 
              },
              { 
                sessionId: this.sessionId, 
                testId: this.testId, 
                testName: this.test?.title || 'Unknown Test'
              }
            );
            this.studentActivityService.sendActivity(testStartedActivity);
            
            // Also log test started event to database for persistence
            this.logEvent('TEST_STARTED', `Started test: ${this.test?.title}`);
          },
          error: (error) => {
            console.error('Error creating exam session:', error);
            // Continue even if session creation fails
            this.sessionId = 0;
            this.studentActivityService.connect();
          }
        });
      },
      error: (error) => {
        console.error('Error loading questions:', error);
        this.errorMessage = 'Failed to load questions. Please try again.';
        this.loading = false;
      }
    });
  }

  startTimer(): void {
    this.timerSubscription = interval(1000).subscribe(() => {
      this.timeRemaining--;
      
      // Auto-submit when time runs out
      if (this.timeRemaining <= 0) {
        this.autoSubmitTest();
      }
      
      // Warning at 5 minutes remaining
      if (this.timeRemaining === 300) {
        alert('⚠️ 5 minutes remaining!');
        this.logEvent('TIME_WARNING', '5 minutes remaining');
      }
    });
  }

  get currentQuestion(): Question | undefined {
    return this.questions[this.currentQuestionIndex];
  }

  get progress(): number {
    return this.questions.length > 0 
      ? (this.currentQuestionIndex + 1) / this.questions.length * 100 
      : 0;
  }

  get answeredCount(): number {
    return Object.keys(this.answers).length;
  }

  get formattedTime(): string {
    const minutes = Math.floor(this.timeRemaining / 60);
    const seconds = this.timeRemaining % 60;
    return `${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}`;
  }

  selectAnswer(optionIndex: number): void {
    if (this.currentQuestion && this.currentQuestion.id) {
      this.answers[this.currentQuestion.id] = optionIndex;
      
      // Log answer selected with letter (A, B, C, D)
      const optionLetter = ['A', 'B', 'C', 'D'][optionIndex];
      
      // Send real-time activity via WebSocket
      const answerActivity = this.studentActivityService.createActivity(
        'QUESTION_ANSWERED',
        'LOW',
        `Question ${this.currentQuestionIndex + 1}: Selected option ${optionLetter}`,
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        },
        { 
          questionNumber: this.currentQuestionIndex + 1,
          selectedOption: optionLetter,
          questionId: this.currentQuestion.id
        }
      );
      this.studentActivityService.sendActivity(answerActivity);
      
      // Also log to database
      this.logEvent('ANSWER_SELECTED', `Question ${this.currentQuestionIndex + 1}: Option ${optionLetter}`);
    }
  }

  nextQuestion(): void {
    if (this.currentQuestionIndex < this.questions.length - 1) {
      this.currentQuestionIndex++;
      this.logEvent('QUESTION_NAVIGATED', `Moved to question ${this.currentQuestionIndex + 1}`);
    }
  }

  previousQuestion(): void {
    if (this.currentQuestionIndex > 0) {
      this.currentQuestionIndex--;
      this.logEvent('QUESTION_NAVIGATED', `Moved to question ${this.currentQuestionIndex + 1}`);
    }
  }

  goToQuestion(index: number): void {
    this.currentQuestionIndex = index;
    this.logEvent('QUESTION_NAVIGATED', `Jumped to question ${index + 1}`);
  }

  isAnswered(index: number): boolean {
    const question = this.questions[index];
    return question && question.id ? this.answers[question.id] !== undefined : false;
  }

  submitTest(): void {
    // Confirm submission
    const unanswered = this.questions.length - this.answeredCount;
    if (unanswered > 0) {
      const confirm = window.confirm(
        `You have ${unanswered} unanswered question(s). Do you want to submit anyway?`
      );
      if (!confirm) return;
    } else {
      const confirm = window.confirm('Are you sure you want to submit your test?');
      if (!confirm) return;
    }
    
    this.performSubmission();
  }

  autoSubmitTest(): void {
    alert('⏰ Time is up! Your test will be submitted automatically.');
    this.performSubmission();
  }

  performSubmission(): void {
    this.submitting = true;
    
    // Stop the timer
    if (this.timerSubscription) {
      this.timerSubscription.unsubscribe();
    }
    
    // Validate answers format
    console.log('📋 Validating submission data...');
    console.log('Test ID:', this.testId);
    console.log('Answers object:', this.answers);
    console.log('Answers type:', typeof this.answers);
    console.log('Number of answers:', Object.keys(this.answers).length);
    console.log('Sample answer:', Object.entries(this.answers)[0]);
    
    // Ensure all values are numbers
    const validatedAnswers: { [key: number]: number } = {};
    for (const [questionId, optionIndex] of Object.entries(this.answers)) {
      const qId = Number(questionId);
      const oIdx = Number(optionIndex);
      if (isNaN(qId) || isNaN(oIdx)) {
        console.error('Invalid answer format:', questionId, '→', optionIndex);
        this.errorMessage = 'Invalid answer format detected. Please refresh and try again.';
        this.submitting = false;
        return;
      }
      validatedAnswers[qId] = oIdx;
    }
    
    console.log('✅ Validated answers:', validatedAnswers);
    console.log('📤 Submitting test to backend...');
    
    this.testService.submitTest(this.testId, validatedAnswers).subscribe({
      next: (result) => {
        console.log('✅ Test submitted successfully:', result);
        
        // Send real-time TEST_SUBMITTED activity
        const submitActivity = this.studentActivityService.createActivity(
          'TEST_SUBMITTED',
          'LOW',
          `Submitted with ${this.answeredCount} answers`,
          { 
            id: this.currentUser.id, 
            name: this.currentUser.username,
            email: this.currentUser.email 
          },
          { 
            sessionId: this.sessionId, 
            testId: this.testId, 
            testName: this.test?.title || 'Unknown Test'
          },
          {
            answeredCount: this.answeredCount,
            totalQuestions: this.questions.length,
            tabSwitchCount: this.tabSwitchCount,
            timeTaken: (this.test!.duration * 60) - this.timeRemaining
          }
        );
        this.studentActivityService.sendActivity(submitActivity);
        
        // Small delay to ensure WebSocket message is sent
        setTimeout(() => {
          // Disconnect WebSocket after submission
          this.studentActivityService.disconnect();
          
          // Also log to database
          this.logEvent('TEST_SUBMITTED', `Submitted with ${this.answeredCount} answers`);
          
          // Navigate to results with the result data
          this.router.navigate(['/results'], { 
            queryParams: { testId: this.testId },
            state: { testResult: result }  // Pass the result directly
          });
        }, 500);
      },
      error: (error) => {
        console.error('❌ Error submitting test:', error);
        console.error('Error details:', error.error);
        console.error('Full error object:', JSON.stringify(error, null, 2));
        
        // Extract error message
        let errorMessage = 'Failed to submit test. Please try again.';
        
        if (error.error) {
          if (typeof error.error === 'string') {
            errorMessage = error.error;
          } else if (error.error.message) {
            errorMessage = error.error.message;
          } else if (error.error.error) {
            errorMessage = error.error.error;
          }
        } else if (error.message) {
          errorMessage = error.message;
        }
        
        // Add network error handling
        if (error.status === 0) {
          errorMessage = 'Network error. Please check your connection and try again.';
        } else if (error.status === 401) {
          errorMessage = 'Authentication failed. Please login again.';
        } else if (error.status === 403) {
          errorMessage = 'You do not have permission to submit this test.';
        } else if (error.status === 404) {
          errorMessage = 'Test not found. Please contact administrator.';
        } else if (error.status === 500) {
          errorMessage = 'Server error. Please contact administrator.';
        }
        
        this.errorMessage = errorMessage;
        this.submitting = false;
        
        // Show alert to user
        alert('❌ ' + errorMessage);
        
        // Restart timer if submission failed
        if (this.timeRemaining > 0) {
          this.startTimer();
        }
      }
    });
  }

  // Monitoring Methods
  setupVisibilityMonitoring(): void {
    document.addEventListener('visibilitychange', () => {
      if (document.hidden) {
        this.tabSwitchCount++;
        this.suspiciousActivityDetected = true;
        
        // Send real-time activity via WebSocket for admin monitoring
        const severity = this.tabSwitchCount >= 3 ? 'CRITICAL' : 'HIGH';
        const tabSwitchActivity = this.studentActivityService.createActivity(
          'TAB_SWITCH',
          severity,
          `Tab switched (count: ${this.tabSwitchCount})`,
          { 
            id: this.currentUser.id, 
            name: this.currentUser.username,
            email: this.currentUser.email 
          },
          { 
            sessionId: this.sessionId, 
            testId: this.testId, 
            testName: this.test?.title || 'Unknown Test'
          },
          { tabSwitchCount: this.tabSwitchCount }
        );
        this.studentActivityService.sendActivity(tabSwitchActivity);
        
        // Also log to database for persistence
        this.logEvent('TAB_SWITCH', `Tab switched (count: ${this.tabSwitchCount})`);
        
        if (this.tabSwitchCount >= 3) {
          alert('⚠️ Warning: Multiple tab switches detected! This activity is being monitored.');
        }
      }
    });
    
    window.addEventListener('blur', () => {
      // Send real-time activity
      const blurActivity = this.studentActivityService.createActivity(
        'WINDOW_BLUR',
        'MEDIUM',
        'Window lost focus',
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        }
      );
      this.studentActivityService.sendActivity(blurActivity);
      
      // Also log to database
      this.logEvent('WINDOW_BLUR', 'Window lost focus');
    });
    
    window.addEventListener('focus', () => {
      // Send real-time activity
      const focusActivity = this.studentActivityService.createActivity(
        'WINDOW_FOCUS',
        'LOW',
        'Window gained focus',
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        }
      );
      this.studentActivityService.sendActivity(focusActivity);
      
      // Also log to database
      this.logEvent('WINDOW_FOCUS', 'Window gained focus');
    });
  }

  setupCopyPasteMonitoring(): void {
    document.addEventListener('copy', (e) => {
      this.suspiciousActivityDetected = true;
      
      // Send real-time activity via WebSocket (CRITICAL severity)
      const copyActivity = this.studentActivityService.createActivity(
        'COPY_ATTEMPT',
        'CRITICAL',
        'Copy attempt detected and blocked',
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        }
      );
      this.studentActivityService.sendActivity(copyActivity);
      
      // Also log to database
      this.logEvent('COPY_ATTEMPT', 'Copy attempt detected');
      
      e.preventDefault();
      alert('⚠️ Copying is not allowed during the test!');
    });
    
    document.addEventListener('paste', (e) => {
      this.suspiciousActivityDetected = true;
      
      // Send real-time activity via WebSocket (HIGH severity)
      const pasteActivity = this.studentActivityService.createActivity(
        'PASTE_ATTEMPT',
        'HIGH',
        'Paste attempt detected',
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        }
      );
      this.studentActivityService.sendActivity(pasteActivity);
      
      // Also log to database
      this.logEvent('PASTE_ATTEMPT', 'Paste attempt detected');
    });
    
    document.addEventListener('contextmenu', (e) => {
      e.preventDefault();
      
      // Send real-time activity via WebSocket
      const rightClickActivity = this.studentActivityService.createActivity(
        'RIGHT_CLICK',
        'MEDIUM',
        'Right-click detected and blocked',
        { 
          id: this.currentUser.id, 
          name: this.currentUser.username,
          email: this.currentUser.email 
        },
        { 
          sessionId: this.sessionId, 
          testId: this.testId, 
          testName: this.test?.title || 'Unknown Test'
        }
      );
      this.studentActivityService.sendActivity(rightClickActivity);
      
      // Also log to database
      this.logEvent('RIGHT_CLICK', 'Right-click detected');
    });
  }

  setupBeforeUnloadHandler(): void {
    window.addEventListener('beforeunload', (e) => {
      if (!this.submitting) {
        e.preventDefault();
        e.returnValue = 'You have an ongoing test. Are you sure you want to leave?';
      }
    });
  }

  logEvent(eventType: string, details: string): void {
    if (!this.currentUser) return;
    
    const event = {
      studentId: this.currentUser.userId || this.currentUser.id,
      testId: this.testId,
      eventType: eventType,
      details: details,
      timestamp: new Date().toISOString()
    };
    
    console.log('Logging event:', event);
    
    this.eventService.logEvent(event).subscribe({
      next: () => {
        // Event logged successfully
      },
      error: (error: any) => {
        console.error('Failed to log event:', error);
      }
    });
  }
}
