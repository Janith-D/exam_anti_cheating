package com.example.anti_cheating_backend.repo;

import com.example.anti_cheating_backend.entity.Student;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface StudentRepo extends JpaRepository<Student,Long> {
    Student findByUserName(String userName);
    Student findByEmail(Student email);

    List<Student> findByIsActiveTrue();
}
