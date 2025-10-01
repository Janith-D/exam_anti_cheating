package com.example.anti_cheating_backend.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Entity
@Data
@Table(name = "students")
@AllArgsConstructor
@NoArgsConstructor
public class Student {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(unique = true,nullable = false)
    private String userName;
    @Column(unique = true,nullable = false)
    private String email;
    @Column(nullable = false)
    private String password;
    private String firstName;
    private String lastName;
    @Column(name = "student_id", unique = true)
    private String studentId;
    @Enumerated(EnumType.STRING)
    private Enums.UserRole role = Enums.UserRole.STUDENT;
    private Boolean isActive = true;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    @OneToMany(mappedBy = "student", cascade = CascadeType.ALL)
    private List<Event> events;
    @OneToMany(mappedBy = "student",cascade = CascadeType.ALL)
    private List<Enrollment> enrollments;
}
