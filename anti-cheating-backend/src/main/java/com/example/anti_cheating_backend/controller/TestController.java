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

import com.example.anti_cheating_backend.dto.CreateTestRequest;
import com.example.anti_cheating_backend.entity.Exam;
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
    public ResponseEntity<Test> createTest(@RequestBody CreateTestRequest request){
        try{
            String createdBy = SecurityContextHolder.getContext().getAuthentication().getName();
            LOGGER.info("=== CREATE TEST REQUEST ===");
            LOGGER.info("Authenticated user: " + createdBy);
            LOGGER.info("Authorities: " + SecurityContextHolder.getContext().getAuthentication().getAuthorities());
            LOGGER.info("Test data received - Title: " + request.getTitle() + ", Exam ID: " + request.getExamId());
            
            // Create Test entity from DTO
            Test test = new Test();
            test.setTitle(request.getTitle());
            test.setDescription(request.getDescription());
            test.setDuration(request.getDuration());
            test.setTestOrder(request.getTestOrder());
            test.setPassingScore(request.getPassingScore());
            test.setTotalMarks(request.getTotalMarks());
            
            // Create Exam object with just the ID
            Exam exam = new Exam();
            exam.setId(request.getExamId());
            test.setExam(exam);
            
            Test savedTest = testService.createTest(test, createdBy);
            LOGGER.info("Test created successfully with ID: " + savedTest.getId());
            return ResponseEntity.ok(savedTest);
        } catch (Exception e){
            LOGGER.severe("Error creating test: " + e.getMessage());
            e.printStackTrace();
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

    @GetMapping("/exam/{examId}")
    @PreAuthorize("hasRole('ADMIN') or hasRole('STUDENT')")
    public ResponseEntity<List<Test>> getTestsByExam(@PathVariable Long examId) {
        try {
            LOGGER.info("Fetching tests for exam ID: " + examId);
            List<Test> tests = testService.getTestsByExam(examId);
            LOGGER.info("Found " + tests.size() + " test(s) for exam " + examId);
            return ResponseEntity.ok(tests);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching tests for exam " + examId + ": " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @DeleteMapping("/{testId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> deleteTest(
            @PathVariable Long testId,
            @org.springframework.web.bind.annotation.RequestParam(required = false, defaultValue = "false") boolean force) {
        try {
            LOGGER.info("=== DELETE TEST REQUEST ===");
            LOGGER.info("Test ID: " + testId);
            LOGGER.info("Force delete: " + force);
            LOGGER.info("Requested by: " + SecurityContextHolder.getContext().getAuthentication().getName());
            
            testService.deleteTest(testId, force);
            
            if (force) {
                LOGGER.info("Test " + testId + " and associated results deleted successfully");
                return ResponseEntity.ok(Map.of(
                    "message", "Test and all associated student results deleted successfully",
                    "warning", "Student data has been permanently removed"
                ));
            }
            LOGGER.info("Test " + testId + " deleted successfully");
            return ResponseEntity.ok(Map.of("message", "Test deleted successfully"));
        } catch (RuntimeException e) {
            LOGGER.severe("=== DELETE TEST FAILED ===");
            LOGGER.severe("Test ID: " + testId);
            LOGGER.severe("Error: " + e.getMessage());
            e.printStackTrace();
            
            return ResponseEntity.badRequest().body(Map.of(
                "error", e.getMessage(),
                "testId", testId,
                "suggestion", "Test ID " + testId + " does not exist. Please refresh the page to see current tests."
            ));
        }
    }

    @GetMapping
    @PreAuthorize("hasRole('STUDENT') or hasRole('ADMIN')")
    public ResponseEntity<List<Test>> getAvailableTests() {
        try {
            List<Test> tests = testService.getAvailableTests();
            LOGGER.info("Fetched " + tests.size() + " available tests");
            return ResponseEntity.ok(tests);
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching available tests: " + e.getMessage());
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/all")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<?> getAllTests() {
        try {
            List<Test> tests = testService.getAvailableTests();
            LOGGER.info("=== ALL TESTS REQUEST ===");
            LOGGER.info("Total tests in database: " + tests.size());
            
            // Create detailed response
            List<Map<String, Object>> testDetails = tests.stream().map(test -> {
                Map<String, Object> details = new java.util.HashMap<>();
                details.put("id", test.getId());
                details.put("title", test.getTitle());
                details.put("description", test.getDescription());
                details.put("duration", test.getDuration());
                details.put("examId", test.getExam() != null ? test.getExam().getId() : null);
                details.put("examTitle", test.getExam() != null ? test.getExam().getTitle() : null);
                details.put("createdBy", test.getCreatedBy());
                details.put("createdAt", test.getCreatedAt());
                return details;
            }).collect(java.util.stream.Collectors.toList());
            
            return ResponseEntity.ok(Map.of(
                "count", tests.size(),
                "tests", testDetails
            ));
        } catch (RuntimeException e) {
            LOGGER.severe("Error fetching all tests: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
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
