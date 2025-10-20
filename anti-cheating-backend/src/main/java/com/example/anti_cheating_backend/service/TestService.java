package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.repo.TestRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.logging.Logger;

@Service
public class TestService {

    private static final Logger LOGGER = Logger.getLogger
            (TestService.class.getName());

    @Autowired
    private TestRepo testRepo;

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
    public List<Test> getAvailableTests(){
        return testRepo.findAll();
    }

}
