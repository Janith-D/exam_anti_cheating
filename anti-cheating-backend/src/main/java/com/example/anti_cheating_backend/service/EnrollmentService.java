package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class EnrollmentService {

    private static final Logger LOGGER = Logger.getLogger(EnrollmentService.class.getName());

    @Autowired
    private EnrollmentRepo enrollmentRepo;
    @Autowired
    private StudentRepo studentRepo;
    @Value("${ml.service.url:http://localhost:5000}")
    private String mlService;
    @Value("${ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public Enrollment enroll(Long studentId, String imageBase64) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        // Delete existing enrollment if any
        Enrollment existing = enrollmentRepo.findByStudentId(studentId);
        if (existing != null) {
            enrollmentRepo.delete(existing);
        }

        if (!mlServiceEnabled) {
            LOGGER.info("ML service disabled, using mock enrollment");
            return createMockEnrollment(student);
        }

        Map<String, Object> payload = Map.of("studentId", studentId, "image", imageBase64);
        LOGGER.info("Sending enrollment request to ML service: " + payload);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(mlService + "/enroll", payload, Map.class);
        } catch (RestClientException e) {
            LOGGER.severe("ML service connection failed: " + e.getMessage());
            throw new RuntimeException("ML service connection failed: " + e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            LOGGER.severe("ML enrollment failed: HTTP " + response.getStatusCode());
            throw new RuntimeException("ML enrollment failed: HTTP " + response.getStatusCode());
        }
        Map<String, Object> result = response.getBody();
        LOGGER.info("ML service response: " + (result != null ? result : "null"));
        if (result == null || !Boolean.TRUE.equals(result.get("success")) || (result.get("embedding") == null && result.get("embeddin") == null)) {
            String errorMsg = "Enrollment failed: Invalid ML service response";
            if (result != null) {
                errorMsg += " - success: " + result.get("success") + ", embedding: " + result.get("embedding") + ", embeddin: " + result.get("embeddin");
            }
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg);
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setFaceEmbedding(result.get("embedding") != null ? result.get("embedding").toString() : result.get("embeddin").toString());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore((Double) result.getOrDefault("quality", 1.0));

        return enrollmentRepo.save(enrollment);
    }

    private Enrollment createMockEnrollment(Student student) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setFaceEmbedding("mock_embedding_data");
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore(1.0);
        return enrollmentRepo.save(enrollment);
    }

    public Enrollment getEnrollment(Long studentId) {
        return enrollmentRepo.findByStudentId(studentId);
    }
}