package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.StudentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
public class StudentService {

    @Autowired
    private StudentRepo studentRepo;

    public Student findByUsername(String username){
        return studentRepo.findByUserName(username);
    }
    public Optional<Student> findById(Long id){
        return studentRepo.findById(id);
    }
    public List<Student> getAllActiveStudents(){
        return studentRepo.findByIsActiveTrue();
    }
    public Student updateStudent(Long id,Student studentDetails){
        Student student = studentRepo.findById(id)
                .orElseThrow(() -> new RuntimeException("Student not found"));
        student.setFirstName(studentDetails.getFirstName());
        student.setLastName(studentDetails.getLastName());
        student.setEmail(studentDetails.getEmail());
        student.setUpdatedAt(LocalDateTime.now());
        return studentRepo.save(student);
    }

}
