package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Question;
import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.repo.QuestionRepo;
import com.example.anti_cheating_backend.repo.TestRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.logging.Logger;

@Service
public class QuestionService {

    private static final Logger LOGGER = Logger.getLogger(QuestionService.class.getName());

    @Autowired
    private QuestionRepo questionRepo;
    @Autowired
    private TestRepo testRepo;

    public Question createQuestion(Question question) {
        if (question.getOptions() == null || question.getOptions().size() != 4) {
            LOGGER.severe("Question must have exactly 4 options");
            throw new IllegalArgumentException("Question must have exactly 4 options");
        }
        if (question.getCorrectOption() < 0 || question.getCorrectOption() >= 4) {
            LOGGER.severe("Correct option must be between 0 and 3");
            throw new IllegalArgumentException("Correct option must be between 0 and 3");
        }
        if (question.getTest() == null || question.getTest().getId() == null) {
            LOGGER.severe("Question must be associated with a valid test");
            throw new IllegalArgumentException("Question must be associated with a valid test");
        }
        Test test = testRepo.findById(question.getTest().getId())
                .orElseThrow(() -> new RuntimeException("Test not found: " + question.getTest().getId()));
        question.setTest(test);
        Question savedQuestion = questionRepo.save(question);
        LOGGER.info("Created question: " + savedQuestion.getId() + " for test: " + test.getId());
        return savedQuestion;
    }

    public List<Question> getQuestionsByTest(Long testId) {
        return questionRepo.findByTestId(testId);
    }

    public List<Question> getQuestionsByTopic(String topic) {
        return questionRepo.findByTopic(topic);
    }

    public Question getQuestion(Long questionId) {
        return questionRepo.findById(questionId)
                .orElseThrow(() -> new RuntimeException("Question not found: " + questionId));
    }
}
