package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.Enums;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ExamRepo extends JpaRepository<Exam, Long> {
    List<Exam> findByStatus(Enums.ExamStatus status);
    List<Exam> findByStatusIn(List<Enums.ExamStatus> statuses);
    List<Exam> findByCreatedBy(String createdBy);
}
