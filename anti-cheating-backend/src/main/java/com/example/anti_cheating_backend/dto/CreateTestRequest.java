package com.example.anti_cheating_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateTestRequest {
    private String title;
    private String description;
    private int duration;
    private Long examId;  // Optional - tests can be created independently and attached to exams later
    private Integer testOrder;
    private Double passingScore;
    private Double totalMarks;
    private String type; // MCQ or ESSAY
}
