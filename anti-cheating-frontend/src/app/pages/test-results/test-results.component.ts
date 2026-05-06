import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TestService } from '../../Services/test.service.service';
import { QuestionService } from '../../Services/question.service.service';
import { TestResult } from '../../models/result.model';
import { Question } from '../../models/question.model';

interface EssayEntry {
  questionId: string;
  questionText: string;
  answer: string;
}

interface ResultWithEssays extends TestResult {
  parsedEssays?: EssayEntry[];
}

@Component({
  selector: 'app-test-results',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './test-results.component.html',
  styleUrl: './test-results.component.css'
})
export class TestResultsComponent implements OnInit {
  testId: number = 0;
  results: ResultWithEssays[] = [];
  questions: Question[] = [];
  loading = true;
  error = '';

  // Grading modal
  selectedResult: ResultWithEssays | null = null;
  gradeScore: number = 0;
  grading = false;
  gradeSuccess = '';
  gradeError = '';

  constructor(
    private route: ActivatedRoute,
    private router: Router,
    private testService: TestService,
    private questionService: QuestionService
  ) {}

  ngOnInit() {
    this.route.params.subscribe(params => {
      this.testId = +params['testId'];
      if (this.testId) {
        this.loadQuestionsAndResults();
      } else {
        this.error = 'No test ID specified.';
        this.loading = false;
      }
    });
  }

  loadQuestionsAndResults() {
    this.loading = true;
    this.error = '';
    
    this.questionService.getQuestionsByTest(this.testId).subscribe({
      next: (questions) => {
        this.questions = questions;
        this.loadResults();
      },
      error: (err) => {
        console.error('Failed to load questions, continuing to results anyway', err);
        // Continue loading results even if questions fail
        this.loadResults();
      }
    });
  }

  loadResults() {
    this.testService.getTestResults(this.testId).subscribe({
      next: (results) => {
        this.results = results.map(r => this.enrichWithEssays(r));
        this.loading = false;
      },
      error: (err) => {
        this.error = 'Failed to load results: ' + (err.error?.message || err.message);
        this.loading = false;
      }
    });
  }

  private enrichWithEssays(result: TestResult): ResultWithEssays {
    const enriched: ResultWithEssays = { ...result };
    if (result.essayAnswersJson) {
      try {
        const parsed: Record<string, string> = JSON.parse(result.essayAnswersJson);
        enriched.parsedEssays = Object.entries(parsed).map(([qId, answer]) => {
          const numQId = parseInt(qId, 10);
          const question = this.questions.find(q => q.id === numQId);
          
          return {
            questionId: qId,
            questionText: question ? question.text : `Question ID: ${qId}`,
            answer: answer
          };
        });
      } catch {
        enriched.parsedEssays = [];
      }
    }
    return enriched;
  }

  openGradeModal(result: ResultWithEssays) {
    this.selectedResult = result;
    this.gradeScore = result.scorePercentage ?? 0;
    this.gradeSuccess = '';
    this.gradeError = '';
  }

  closeGradeModal() {
    this.selectedResult = null;
  }

  submitGrade() {
    if (!this.selectedResult?.id) return;
    if (this.gradeScore < 0 || this.gradeScore > 100) {
      this.gradeError = 'Score must be between 0 and 100.';
      return;
    }
    this.grading = true;
    this.gradeError = '';
    this.testService.gradeEssay(this.selectedResult.id, this.gradeScore).subscribe({
      next: (updated) => {
        // Update the result in the list
        const idx = this.results.findIndex(r => r.id === updated.id);
        if (idx !== -1) {
          this.results[idx] = this.enrichWithEssays(updated);
        }
        this.gradeSuccess = `Score saved: ${this.gradeScore}%`;
        this.grading = false;
        setTimeout(() => this.closeGradeModal(), 1500);
      },
      error: (err) => {
        this.gradeError = 'Failed to save grade: ' + (err.error?.message || err.message);
        this.grading = false;
      }
    });
  }

  deleteResult(result: TestResult) {
    if (!result.id) return;
    
    const studentName = result.student?.username || result.student?.userName || 'this student';
    if (confirm(`Are you sure you want to delete the result for student "${studentName}"? This action cannot be undone.`)) {
      this.testService.deleteTestResult(result.id).subscribe({
        next: () => {
          this.results = this.results.filter(r => r.id !== result.id);
        },
        error: (err) => {
          alert('Failed to delete result: ' + (err.error?.message || err.message));
        }
      });
    }
  }

  pendingCount(): number {
    return this.results.filter(r => r.status === 'PENDING_REVIEW').length;
  }

  goBack() {
    this.router.navigate(['/admin/dashboard']);
  }
}
