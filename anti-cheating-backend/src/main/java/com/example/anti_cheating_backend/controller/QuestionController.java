package com.example.anti_cheating_backend.controller;

import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Question;
import com.example.anti_cheating_backend.service.QuestionService;

@RestController
@RequestMapping("/api/questions")
public class QuestionController {

    private static final Logger LOGGER = Logger.getLogger
            (QuestionController.class.getName());

    @Autowired
    private QuestionService questionService;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Question> createQuestion(@RequestBody Question question) {
        try {
            Question savedQuestion = questionService.createQuestion(question);
            return ResponseEntity.ok(savedQuestion);
        } catch ( RuntimeException e) {
            LOGGER.severe("Error creating question: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/test/{testId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<Question>> getQuestionsByTest(@PathVariable Long testId) {
        try {
            List<Question> questions = questionService.getQuestionsByTest(testId);
            return ResponseEntity.ok(questions);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching questions for test " + testId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/topic/{topic}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Question>> getQuestionsByTopic(@PathVariable String topic) {
        try {
            List<Question> questions = questionService.getQuestionsByTopic(topic);
            return ResponseEntity.ok(questions);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching questions by topic " + topic + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{questionId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Question> getQuestion(@PathVariable Long questionId) {
        try {
            Question question = questionService.getQuestion(questionId);
            return ResponseEntity.ok(question);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching question " + questionId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}
