package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(name = "questions")
@Data
@AllArgsConstructor
@NoArgsConstructor
public class Question {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @ManyToOne
    @JoinColumn(name = "test_id",nullable = false)
    private Test test;
    private String text;
    @ElementCollection
    @CollectionTable(name = "question_option",joinColumns =
    @JoinColumn(name = "question_id"))
    @Column(name = "option")
    private List<String> options;
    private int correctOption;
    private String topic;
}
