package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.TestResult;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TestResultRepo extends JpaRepository<TestResult,Long> {

    List<TestResult> findByStudentId(Long studentId);
    List<TestResult> findByTestId(Long testId);
}
