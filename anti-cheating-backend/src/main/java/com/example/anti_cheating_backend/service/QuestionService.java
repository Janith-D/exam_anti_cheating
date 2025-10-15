package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.repo.QuestionRepo;
import com.example.anti_cheating_backend.repo.TestRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class QuestionService {

    @Autowired
    private QuestionRepo questionRepo;
    @Autowired
    private TestRepo testRepo;


}
