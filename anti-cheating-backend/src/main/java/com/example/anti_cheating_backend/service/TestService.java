package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.entity.TestResult;
import com.example.anti_cheating_backend.repo.ExamRepo;
import com.example.anti_cheating_backend.repo.QuestionRepo;
import com.example.anti_cheating_backend.repo.TestRepo;
import com.example.anti_cheating_backend.repo.TestResultRepo;

@Service
public class TestService {

    private static final Logger LOGGER = Logger.getLogger
            (TestService.class.getName());

    @Autowired
    private TestRepo testRepo;
    
    @Autowired
    private QuestionRepo questionRepo;
    
    @Autowired
    private TestResultRepo testResultRepo;
    
    @Autowired
    private ExamRepo examRepo;

    public Test createTest(Test test,String createdBy){
        if (test.getTitle() == null || test.getTitle().isEmpty()){
            LOGGER.severe("Test title cannot be empty");
            throw new IllegalArgumentException("Test title cannot be empty");
        }
        if (test.getDuration() <= 0){
            LOGGER.severe("Test duration must be positive");
            throw new IllegalArgumentException("Test duration must be positive");
        }
        
        // CRITICAL FIX: Validate and load the exam from database
        if (test.getExam() == null || test.getExam().getId() == null) {
            LOGGER.severe("Test must be associated with a valid exam");
            throw new IllegalArgumentException("Test must be associated with a valid exam");
        }
        
        // Load the exam from database to ensure it exists
        Exam exam = examRepo.findById(test.getExam().getId())
                .orElseThrow(() -> {
                    LOGGER.severe("Exam not found with ID: " + test.getExam().getId());
                    return new RuntimeException("Exam not found with ID: " + test.getExam().getId());
                });
        
        // Set the exam relationship explicitly
        test.setExam(exam);
        test.setCreatedBy(createdBy);
        test.setCreatedAt(LocalDateTime.now());
        
        Test savedTest = testRepo.save(test);
        LOGGER.info("Created test: " + savedTest.getId() + " for exam: " + exam.getId() + " by " + createdBy);
        return savedTest;
    }

    public Test getTest(Long testId){
        return testRepo.findById(testId)
                .orElseThrow(()-> new RuntimeException("Test not found : "+ testId));
    }
    
    @Transactional
    public void deleteTest(Long testId){
        deleteTest(testId, false); // Default: don't force delete
    }
    
    @Transactional
    public void deleteTest(Long testId, boolean forceDelete){
        Test test = testRepo.findById(testId)
                .orElseThrow(()-> new RuntimeException("Test not found : "+ testId));
        
        LOGGER.info("Attempting to delete test: " + testId + " (force: " + forceDelete + ")");
        
        // Check if there are test results (students have taken this test)
        List<TestResult> testResults = testResultRepo.findByTestId(testId);
        if (!testResults.isEmpty() && !forceDelete) {
            LOGGER.warning("Cannot delete test " + testId + " - " + testResults.size() + " student(s) have taken it");
            throw new RuntimeException("Cannot delete test. " + testResults.size() + " student(s) have already taken this test. Use force delete to remove it anyway (this will delete student results).");
        }
        
        // If force delete, remove all test results first
        if (!testResults.isEmpty() && forceDelete) {
            LOGGER.warning("Force deleting test " + testId + " with " + testResults.size() + " student results");
            testResultRepo.deleteAll(testResults);
            LOGGER.info("Deleted " + testResults.size() + " test results for test: " + testId);
        }
        
        // Delete all questions associated with this test
        int deletedQuestions = questionRepo.findByTestId(testId).size();
        questionRepo.deleteByTestId(testId);
        LOGGER.info("Deleted " + deletedQuestions + " question(s) for test: " + testId);
        
        // Log exam association if exists
        if (test.getExam() != null) {
            LOGGER.info("Removing test " + testId + " from exam: " + test.getExam().getId() + " (" + test.getExam().getTitle() + ")");
        }
        
        // Delete the test (exam association will be handled by cascade)
        testRepo.delete(test);
        LOGGER.info("Successfully deleted test: " + testId);
    }
    
    public List<Test> getAvailableTests(){
        return testRepo.findAll();
    }

    public List<Test> getTestsByExam(Long examId) {
        return testRepo.findByExamId(examId);
    }

}
