package com.example.anti_cheating_backend.controller;

import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.example.anti_cheating_backend.entity.Test;
import com.example.anti_cheating_backend.entity.TestResult;
import com.example.anti_cheating_backend.repo.StudentRepo;
import com.example.anti_cheating_backend.service.TestResultService;
import com.example.anti_cheating_backend.service.TestService;

@RestController
@RequestMapping("/api/test")
public class TestController {

    private static final Logger LOGGER = Logger.getLogger
            (TestController.class.getName());

    @Autowired
    private TestService testService;
    @Autowired
    private TestResultService testResultService;
    @Autowired
    private StudentRepo studentRepo;

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Test> createTest(@RequestBody Test test){
        try{
            String createdBy = SecurityContextHolder.getContext().getAuthentication().getName();
            Test savedTest = testService.createTest(test,createdBy);
            return ResponseEntity.ok(savedTest);
        } catch (Exception e){
            LOGGER.severe("Error creating test: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
    @GetMapping("/{testId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<Test> getTest(@PathVariable Long testId) {
        try {
            Test test = testService.getTest(testId);
            return ResponseEntity.ok(test);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching test " + testId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{testId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> deleteTest(@PathVariable Long testId) {
        try {
            testService.deleteTest(testId);
            return ResponseEntity.ok().build();
        } catch (RuntimeException e) {
            LOGGER.severe("Error deleting test " + testId + ": " + e.getMessage());
            return ResponseEntity.badRequest().build();
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<List<Test>> getAvailableTests() {
        try {
            List<Test> tests = testService.getAvailableTests();
            return ResponseEntity.ok(tests);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching available tests: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @PostMapping("/{testId}/submit")
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<TestResult> submitTest(@PathVariable Long testId, @RequestBody Map<Long, Integer> answers) {
        try {
            String username = SecurityContextHolder.getContext().getAuthentication().getName();
            LOGGER.info("Submitting test " + testId + " for user: " + username);
            
            // Fetch student by username (not email)
            var student = studentRepo.findByUserName(username);
            if (student == null) {
                LOGGER.severe("Student not found with username: " + username);
                throw new RuntimeException("Student not found: " + username);
            }
            
            LOGGER.info("Found student: ID=" + student.getId() + ", UserName=" + student.getUserName());
            Long studentId = student.getId();
            
            LOGGER.info("Creating test result with " + answers.size() + " answers");
            TestResult result = testResultService.createTestResult(testId, studentId, answers);
            
            LOGGER.info("Test submitted successfully for student " + studentId);
            return ResponseEntity.ok(result);
        } catch ( RuntimeException e) {
            LOGGER.severe("Error submitting test " + testId + ": " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/results/{testId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<TestResult>> getTestResults(@PathVariable Long testId) {
        try {
            List<TestResult> results = testResultService.getResultsByTest(testId);
            return ResponseEntity.ok(results);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching results for test " + testId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/results/student/{studentId}")
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<List<TestResult>> getStudentResults(@PathVariable Long studentId) {
        try {
            List<TestResult> results = testResultService.getResultsByStudent(studentId);
            return ResponseEntity.ok(results);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching results for student " + studentId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }
}
