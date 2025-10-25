package com.example.anti_cheating_backend.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import com.example.anti_cheating_backend.entity.Question;

public interface QuestionRepo extends JpaRepository<Question,Long> {
    List<Question> findByTopic(String topic);
    List<Question> findByTestId(Long testId);
    
    @Transactional
    void deleteByTestId(Long testId);
}
