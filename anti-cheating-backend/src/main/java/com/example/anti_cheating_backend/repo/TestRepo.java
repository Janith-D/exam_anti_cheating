package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Test;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestRepo extends JpaRepository<Test,Long> {
    List<Test> findByExamId(Long examId);
}
