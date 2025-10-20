package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Question;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.entity.TestResult;
import com.example.anti_cheating_backend.repo.QuestionRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import com.example.anti_cheating_backend.repo.TestRepo;
import com.example.anti_cheating_backend.repo.TestResultRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class TestResultService {

    private static final Logger LOGGER = Logger.getLogger(TestResultService.class.getName());

    @Autowired
    private TestResultRepo testResultRepo;
    @Autowired
    private TestRepo testRepo;
    @Autowired
    private QuestionRepo questionRepo;
    @Autowired
    private StudentRepo studentRepo;

    public TestResult createTestResult(Long testId, Long studentId, Map<Long, Integer> answers) {
        Test test = testRepo.findById(testId)
                .orElseThrow(() -> new RuntimeException("Test not found: " + testId));
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        List<Question> questions = questionRepo.findByTestId(testId);
        if (questions.isEmpty()) {
            LOGGER.severe("No questions found for test: " + testId);
            throw new IllegalArgumentException("No questions found for test");
        }

        int correctAnswers = 0;
        for (Question question : questions) {
            Integer studentAnswer = answers.get(question.getId());
            if (studentAnswer != null && studentAnswer == question.getCorrectOption()) {
                correctAnswers++;
            }
        }

        TestResult result = new TestResult();
        result.setTest(test);
        result.setStudent(student);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(questions.size());
        result.setScorePercentage((double) correctAnswers / questions.size() * 100);
        result.setCompletedAt(LocalDateTime.now());

        TestResult savedResult = testResultRepo.save(result);
        LOGGER.info("Created test result for student " + studentId + " on test " + testId + ": " + savedResult.getScorePercentage() + "%");
        return savedResult;
    }

    public List<TestResult> getResultsByTest(Long testId) {
        return testResultRepo.findByTestId(testId);
    }

    public List<TestResult> getResultsByStudent(Long studentId) {
        return testResultRepo.findByStudentId(studentId);
    }
}
