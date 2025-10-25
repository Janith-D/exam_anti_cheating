package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.repo.QuestionRepo;
import com.example.anti_cheating_backend.repo.TestRepo;

@Service
public class TestService {

    private static final Logger LOGGER = Logger.getLogger
            (TestService.class.getName());

    @Autowired
    private TestRepo testRepo;
    
    @Autowired
    private QuestionRepo questionRepo;

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
        Test test = testRepo.findById(testId)
                .orElseThrow(()-> new RuntimeException("Test not found : "+ testId));
        
        // Delete all questions associated with this test first
        questionRepo.deleteByTestId(testId);
        LOGGER.info("Deleted questions for test: " + testId);
        
        // Now delete the test
        testRepo.delete(test);
        LOGGER.info("Deleted test : "+ testId);
    }
    
    public List<Test> getAvailableTests(){
        return testRepo.findAll();
    }

}
