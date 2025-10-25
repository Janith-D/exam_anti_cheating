package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.ExamRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

@Service
public class EnrollmentService {

    private static final Logger LOGGER = Logger.getLogger(EnrollmentService.class.getName());

    @Autowired
    private EnrollmentRepo enrollmentRepo;
    @Autowired
    private StudentRepo studentRepo;
    @Autowired
    private ExamRepo examRepo;
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

    // New exam-based enrollment methods
    public Enrollment enrollInExam(Long studentId, Long examId, String imageBase64) {
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: " + studentId));

        Exam exam = examRepo.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found: " + examId));

        // Validate exam is published and available for enrollment
        if (exam.getStatus() != Enums.ExamStatus.PUBLISHED && exam.getStatus() != Enums.ExamStatus.ONGOING) {
            throw new RuntimeException("Exam is not available for enrollment. Current status: " + exam.getStatus());
        }

        // Check if already enrolled
        if (enrollmentRepo.existsByStudentIdAndExamId(studentId, examId)) {
            throw new RuntimeException("Student is already enrolled in this exam");
        }

        // Check if exam is full
        if (exam.getMaxStudents() != null) {
            long enrolledCount = enrollmentRepo.countByExamIdAndStatus(examId, Enums.EnrollmentStatus.APPROVED);
            if (enrolledCount >= exam.getMaxStudents()) {
                throw new RuntimeException("Exam has reached maximum capacity");
            }
        }

        if (!mlServiceEnabled) {
            LOGGER.info("ML service disabled, using mock enrollment for exam: " + examId);
            return createMockExamEnrollment(student, exam);
        }

        // Call ML service for face verification
        Map<String, Object> payload = Map.of("studentId", studentId, "image", imageBase64);
        LOGGER.info("Sending enrollment request to ML service for exam " + examId);
        
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
        
        if (result == null || !Boolean.TRUE.equals(result.get("success")) || 
            (result.get("embedding") == null && result.get("embeddin") == null)) {
            String errorMsg = "Enrollment failed: Invalid ML service response";
            if (result != null) {
                errorMsg += " - success: " + result.get("success");
            }
            LOGGER.severe(errorMsg);
            throw new RuntimeException(errorMsg);
        }

        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setExam(exam);
        enrollment.setFaceEmbedding(result.get("embedding") != null ? 
                                    result.get("embedding").toString() : 
                                    result.get("embeddin").toString());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore((Double) result.getOrDefault("quality", 1.0));
        enrollment.setStatus(Enums.EnrollmentStatus.VERIFIED);

        return enrollmentRepo.save(enrollment);
    }

    private Enrollment createMockExamEnrollment(Student student, Exam exam) {
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setExam(exam);
        enrollment.setFaceEmbedding("mock_embedding_data_exam_" + exam.getId());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore(1.0);
        enrollment.setStatus(Enums.EnrollmentStatus.APPROVED);
        return enrollmentRepo.save(enrollment);
    }

    public List<Enrollment> getStudentEnrollments(Long studentId) {
        return enrollmentRepo.findAllByStudentId(studentId);
    }

    public List<Enrollment> getExamEnrollments(Long examId) {
        return enrollmentRepo.findByExamId(examId);
    }

    public List<Enrollment> getExamEnrollmentsByStatus(Long examId, Enums.EnrollmentStatus status) {
        return enrollmentRepo.findByExamIdAndStatus(examId, status);
    }

    public Enrollment updateEnrollmentStatus(Long enrollmentId, Enums.EnrollmentStatus status) {
        Enrollment enrollment = enrollmentRepo.findById(enrollmentId)
                .orElseThrow(() -> new RuntimeException("Enrollment not found: " + enrollmentId));
        enrollment.setStatus(status);
        return enrollmentRepo.save(enrollment);
    }

    public boolean isStudentEnrolledInExam(Long studentId, Long examId) {
        return enrollmentRepo.existsByStudentIdAndExamId(studentId, examId);
    }
}