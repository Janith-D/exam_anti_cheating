package com.example.anti_cheating_backend.repo;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.example.anti_cheating_backend.entity.Test;

public interface TestRepo extends JpaRepository<Test,Long> {
    // Find all tests for a specific exam using ManyToMany relationship
    @Query("SELECT t FROM Test t JOIN t.exams e WHERE e.id = :examId")
    List<Test> findByExamId(@Param("examId") Long examId);
}
