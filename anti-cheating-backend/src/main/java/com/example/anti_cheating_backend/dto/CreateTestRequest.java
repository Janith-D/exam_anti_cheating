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
    private Long examId;  // Just the ID, not the full Exam object
    private Integer testOrder;
    private Double passingScore;
    private Double totalMarks;
}
