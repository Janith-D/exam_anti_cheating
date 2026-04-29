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

    public TestResult createTestResult(Long testId, Long studentId, Map<Long, String> answers) {
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
        int mcqCount = 0;
        Map<Long, String> essayAnswers = new java.util.HashMap<>();
        com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();

        for (Question question : questions) {
            String studentAnswerStr = answers.get(question.getId());
            
            if ("ESSAY".equals(question.getType())) {
                if (studentAnswerStr != null) {
                    essayAnswers.put(question.getId(), studentAnswerStr);
                }
            } else {
                mcqCount++;
                if (studentAnswerStr != null) {
                    try {
                        int studentAnswer = Integer.parseInt(studentAnswerStr.toString());
                        if (studentAnswer == question.getCorrectOption()) {
                            correctAnswers++;
                        }
                    } catch (NumberFormatException e) {
                        LOGGER.warning("Invalid MCQ answer format for question " + question.getId() + ": " + studentAnswerStr);
                    }
                }
            }
        }
        
        String essayAnswersJson = null;
        try {
            if (!essayAnswers.isEmpty()) {
                essayAnswersJson = mapper.writeValueAsString(essayAnswers);
            }
        } catch (Exception e) {
            LOGGER.severe("Failed to serialize essay answers: " + e.getMessage());
        }

        TestResult result = new TestResult();
        result.setTest(test);
        result.setStudent(student);
        result.setCorrectAnswers(correctAnswers);
        result.setTotalQuestions(mcqCount > 0 ? mcqCount : questions.size()); // Fallback to avoid dividing by 0 if all are essays
        result.setScorePercentage(mcqCount > 0 ? (double) correctAnswers / mcqCount * 100 : 0);
        result.setCompletedAt(LocalDateTime.now());
        result.setEssayAnswersJson(essayAnswersJson);

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
