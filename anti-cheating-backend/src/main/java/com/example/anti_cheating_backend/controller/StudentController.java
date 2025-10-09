package com.example.anti_cheating_backend.controller;

import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.service.StudentService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("api/students")
public class StudentController {

    @Autowired
    private StudentService studentService;

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #username")
    public ResponseEntity<?> getStudentById(@PathVariable Long id) {
        try {
            Optional<Student> student = studentService.findById(id);
            if (student.isEmpty()) {
                return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
            }
            return ResponseEntity.ok(student.get());
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/username/{username}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #username")
    public ResponseEntity<?> getStudentByUsername(@PathVariable String username) {
        try {
            Student student = studentService.findByUsername(username);
            if (student == null) {
                return ResponseEntity.status(404).body(Map.of("error", "Student not found"));
            }
            return ResponseEntity.ok(student);
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<Student>> getAllActiveStudents() {
        return ResponseEntity.ok(studentService.getAllActiveStudents());
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN') or principal.username == #studentDetails.userName")
    public ResponseEntity<?> updateStudent(@PathVariable Long id, @RequestBody Student studentDetails) {
        try {
            Student updatedStudent = studentService.updateStudent(id, studentDetails);
            return ResponseEntity.ok(Map.of("message", "Student updated", "studentId", updatedStudent.getId()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}
