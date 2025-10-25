import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { ActivatedRoute, Router } from '@angular/router';
import { TestService } from '../../Services/test.service.service';
import { AuthService } from '../../Services/auth.service.service';
import { TestResult } from '../../models/result.model';

@Component({
  selector: 'app-user-results',
  imports: [CommonModule],
  templateUrl: './user-results.component.html',
  styleUrl: './user-results.component.css'
})
export class UserResultsComponent implements OnInit {
  testResult: TestResult | null = null;
  allResults: TestResult[] = [];
  loading = true;
  error = '';
  testId: number = 0;
  showAllResults = false;

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private testService: TestService,
    private authService: AuthService
  ) {}

  ngOnInit() {
    // Check if result was passed via navigation state (immediate after submission)
    const navigation = this.router.getCurrentNavigation();
    const stateResult = navigation?.extras?.state?.['testResult'] || 
                       history.state?.testResult;
    
    if (stateResult) {
      console.log('✅ Result passed via navigation state:', stateResult);
      this.testResult = stateResult;
      this.testId = stateResult.test?.id || 0;
      this.loading = false;
      return;
    }

    // Otherwise, get testId from query params
    this.route.queryParams.subscribe(params => {
      this.testId = +params['testId'];
      if (this.testId) {
        // Load specific test result
        this.showAllResults = false;
        this.loadTestResult();
      } else {
        // No testId provided - show all results
        console.log('ℹ️ No testId provided, loading all results');
        this.showAllResults = true;
        this.loadAllResults();
      }
    });
  }

  loadAllResults() {
    // Get the current user from AuthService
    const currentUser = this.authService.currentUserValue;
    
    console.log('🔍 Loading all results for user:', currentUser);
    
    if (!currentUser || !currentUser.id) {
      console.error('❌ No current user found');
      this.error = 'User session not found. Please login again.';
      this.loading = false;
      return;
    }

    // Get all student's results
    this.testService.getStudentResults(currentUser.id).subscribe({
      next: (results) => {
        console.log('📊 All student results:', results);
        
        // Sort by completion date (most recent first)
        this.allResults = results.sort((a, b) => {
          const dateA = new Date(a.completedAt || 0).getTime();
          const dateB = new Date(b.completedAt || 0).getTime();
          return dateB - dateA;
        });
        
        if (this.allResults.length === 0) {
          this.error = 'You have not completed any tests yet. Start a test to see your results!';
        }
        
        this.loading = false;
      },
      error: (err) => {
        console.error('❌ Error loading results:', err);
        this.error = 'Failed to load results: ' + (err.error?.message || err.message);
        this.loading = false;
      }
    });
  }

  loadTestResult() {
    // Get the current user from AuthService
    const currentUser = this.authService.currentUserValue;
    
    console.log('🔍 Loading test result for:', { testId: this.testId, currentUser });
    
    if (!currentUser) {
      console.error('❌ No current user found');
      this.error = 'User session not found. Please login again.';
      this.loading = false;
      return;
    }

    if (!currentUser.id) {
      console.error('❌ User ID not found:', currentUser);
      this.error = 'User ID not found. Please login again.';
      this.loading = false;
      return;
    }

    console.log('✅ Current user ID:', currentUser.id);

    // Get student's results and find the most recent one for this test
    this.testService.getStudentResults(currentUser.id).subscribe({
      next: (results) => {
        console.log('📊 All student results:', results);
        
        // Find the most recent result for this test
        const testResults = results.filter(r => r.test?.id === this.testId);
        console.log(`🔍 Filtered results for test ${this.testId}:`, testResults);
        
        if (testResults.length > 0) {
          // Sort by completedAt descending and get the most recent
          this.testResult = testResults.sort((a, b) => {
            const dateA = new Date(a.completedAt || 0).getTime();
            const dateB = new Date(b.completedAt || 0).getTime();
            return dateB - dateA;
          })[0];
          console.log('✅ Most recent result:', this.testResult);
        } else {
          console.warn('⚠️ No result found for this test');
          this.error = 'No result found for this test. The submission may still be processing.';
        }
        this.loading = false;
      },
      error: (err) => {
        console.error('❌ Error loading test result:', err);
        this.error = 'Failed to load test result: ' + (err.error?.message || err.message);
        this.loading = false;
      }
    });
  }

  getScoreClass(): string {
    if (!this.testResult) return '';
    const score = this.testResult.scorePercentage;
    if (score >= 80) return 'score-excellent';
    if (score >= 60) return 'score-good';
    if (score >= 40) return 'score-average';
    return 'score-poor';
  }

  getScoreMessage(): string {
    if (!this.testResult) return '';
    const score = this.testResult.scorePercentage;
    if (score >= 80) return 'Excellent! 🎉';
    if (score >= 60) return 'Good Job! 👍';
    if (score >= 40) return 'Keep Practicing 📚';
    return 'Need Improvement 💪';
  }

  getScoreClassForResult(result: TestResult): string {
    const score = result.scorePercentage;
    if (score >= 80) return 'score-excellent';
    if (score >= 60) return 'score-good';
    if (score >= 40) return 'score-average';
    return 'score-poor';
  }

  viewSingleResult(testId: number) {
    if (testId) {
      this.router.navigate(['/results'], { queryParams: { testId } });
    }
  }

  retakeTest() {
    // Navigate back to test page
    const testIdToRetake = this.testResult?.test?.id || this.testId;
    if (testIdToRetake) {
      this.router.navigate(['/test', testIdToRetake]);
    } else {
      console.error('No test ID available for retake');
      this.goToDashboard();
    }
  }

  goToDashboard() {
    // Navigate to student dashboard
    this.router.navigate(['/test-dashboard']);
  }

  viewAllResults() {
    // Could navigate to a page showing all student results
    this.router.navigate(['/test-dashboard']);
  }
}
