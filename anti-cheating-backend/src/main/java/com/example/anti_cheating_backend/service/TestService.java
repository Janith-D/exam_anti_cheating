package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.entity.TestResult;
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

    public Test createTest(Test test,String createdBy){
        if (test.getTitle() == null || test.getTitle().isEmpty()){
            LOGGER.severe("Test title cannot be empty");
            throw new IllegalArgumentException("Test title cannot be empty");
        }
        if (test.getDuration() <= 0){
            LOGGER.severe("Test duration must be positive");
            throw new IllegalArgumentException("Test duration must be positive");
        }
        test.setCreatedBy(createdBy);
        test.setCreatedAt(LocalDateTime.now());
        Test savedTest = testRepo.save(test);
        LOGGER.info("Created test : "+ savedTest.getId() + " By "+ createdBy);
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
        
        // Check if test is associated with an exam (cannot be bypassed)
        if (test.getExam() != null) {
            throw new RuntimeException("Cannot delete test. This test is associated with exam: " + test.getExam().getTitle() + ". Please delete the exam first to remove all its tests.");
        }
        
        // Check if there are test results (students have taken this test)
        List<TestResult> testResults = testResultRepo.findByTestId(testId);
        if (!testResults.isEmpty() && !forceDelete) {
            throw new RuntimeException("Cannot delete test. " + testResults.size() + " student(s) have already taken this test. Deleting it would lose their results.");
        }
        
        // If force delete, remove all test results first
        if (!testResults.isEmpty() && forceDelete) {
            LOGGER.warning("Force deleting test " + testId + " with " + testResults.size() + " student results");
            testResultRepo.deleteAll(testResults);
            LOGGER.info("Deleted " + testResults.size() + " test results for test: " + testId);
        }
        
        // Delete all questions associated with this test
        questionRepo.deleteByTestId(testId);
        LOGGER.info("Deleted questions for test: " + testId);
        
        // Now delete the test
        testRepo.delete(test);
        LOGGER.info("Deleted test : "+ testId);
    }
    
    public List<Test> getAvailableTests(){
        return testRepo.findAll();
    }

    public List<Test> getTestsByExam(Long examId) {
        return testRepo.findByExamId(examId);
    }

}
