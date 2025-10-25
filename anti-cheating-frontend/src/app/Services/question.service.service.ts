import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Question, QuestionCreate } from '../models/question.model';

@Injectable({
  providedIn: 'root'
})
export class QuestionService {
  private apiUrl = 'http://localhost:8080/api/questions';

  constructor(private http: HttpClient) { }

  // Get questions by test ID
  getQuestionsByTest(testId: number): Observable<Question[]> {
    return this.http.get<Question[]>(`${this.apiUrl}/test/${testId}`);
  }

  // Get questions by topic (Admin only)
  getQuestionsByTopic(topic: string): Observable<Question[]> {
    return this.http.get<Question[]>(`${this.apiUrl}/topic/${topic}`);
  }

  // Get single question (Admin only)
  getQuestionById(id: number): Observable<Question> {
    return this.http.get<Question>(`${this.apiUrl}/${id}`);
  }

  // Create question (Admin only)
  createQuestion(question: QuestionCreate): Observable<Question> {
    return this.http.post<Question>(this.apiUrl, question);
  }

  // Delete question (Admin only)
  deleteQuestion(id: number): Observable<void> {
    return this.http.delete<void>(`${this.apiUrl}/${id}`);
  }
}
