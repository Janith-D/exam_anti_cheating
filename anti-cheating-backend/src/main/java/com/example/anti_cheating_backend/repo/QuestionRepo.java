package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Question;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface QuestionRepo extends JpaRepository<Question,Long> {
    List<Question> findBuTestId(Long testId);
    List<Question> findByTopic(String topic);
}
