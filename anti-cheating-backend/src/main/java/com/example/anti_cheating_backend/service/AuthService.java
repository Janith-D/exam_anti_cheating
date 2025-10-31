package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import com.example.anti_cheating_backend.security.JwtUtil;

@Service
public class AuthService implements UserDetailsService {

    private static final Logger LOGGER = Logger.getLogger(AuthService.class.getName());

    @Autowired
    private StudentRepo studentRepo;

    @Autowired
    private EnrollmentRepo enrollmentRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    @Lazy
    private AuthenticationManager authenticationManager;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlServiceUrl;

    @Value("${ml.service.enabled:true}")
    private boolean mlServiceEnabled;

    private final RestTemplate restTemplate = new RestTemplate();

    public Map<String, Object> register(Map<String, Object> payload) {
        String userName = (String) payload.get("userName");
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        String imageBase64 = (String) payload.get("image");
        String role = (String) payload.get("role");
        String firstName = (String) payload.get("firstName");
        String lastName = (String) payload.get("lastName");
        String studentId = (String) payload.get("studentId");

        LOGGER.info("Registering user: " + userName);

        if (studentRepo.findByUserName(userName) != null) {
            throw new RuntimeException("Username already exists");
        }
        if (studentRepo.findByEmail(email) != null) {
            throw new RuntimeException("Email already exists");
        }
        if (studentId != null && studentRepo.findByStudentId(studentId) != null) {
            throw new RuntimeException("Student ID already exists");
        }

        Student student = new Student();
        student.setUserName(userName);
        student.setEmail(email);
        student.setPassword(passwordEncoder.encode(password));
        student.setRole(Enums.UserRole.valueOf(role.toUpperCase()));
        student.setFirstName(firstName);
        student.setLastName(lastName);
        student.setStudentId(studentId);
        student.setIsActive(true);
        student.setCreatedAt(LocalDateTime.now());
        student.setUpdatedAt(LocalDateTime.now());
        student = studentRepo.save(student);

        LOGGER.info("Calling ML service for enrollment: " + mlServiceUrl + "/enroll");
        Enrollment enrollment;
        if (mlServiceEnabled) {
            enrollment = enrollFace(student.getId(), imageBase64);
        } else {
            LOGGER.info("ML service disabled, using mock enrollment");
            enrollment = createMockEnrollment(student);
        }
        if (enrollment == null) {
            studentRepo.delete(student);
            throw new RuntimeException("Face enrollment failed during registration");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("message", "Registered and enrolled successfully");
        response.put("userId", student.getId());
        response.put("enrollmentId", enrollment.getId());

        return response;
    }

    public Map<String, Object> login(Map<String, Object> payload) {
        String userName = (String) payload.get("userName");
        String password = (String) payload.get("password");
        String imageBase64 = (String) payload.get("image");

        LOGGER.info("Logging in user: " + userName);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userName, password)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final UserDetails userDetails = loadUserByUsername(userName);
        final String jwt = jwtUtil.generateToken(userDetails);

        Student student = studentRepo.findByUserName(userName);
        Enrollment enrollment = enrollmentRepo.findByStudentId(student.getId());
        if (enrollment == null) {
            throw new RuntimeException("No enrollment found for user");
        }

        boolean verified;
        if (mlServiceEnabled) {
            verified = verifyFace(student.getId(), imageBase64, enrollment.getFaceEmbedding());
        } else {
            LOGGER.info("ML service disabled, skipping face verification");
            verified = true;
        }
        if (!verified) {
            throw new RuntimeException("Face verification failed during login");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("userId", student.getId()); // ✅ Added missing userId
        response.put("userName", userName);
        response.put("email", student.getEmail()); // ✅ Added email
        response.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        response.put("verified", true);
        response.put("activationAllowed", true);

        return response;
    }

    private Enrollment enrollFace(Long studentId, String imageBase64) {
        Map<String, Object> payload = Map.of("studentId", studentId, "image", imageBase64);
        LOGGER.info("Sending enrollment request to ML service: " + payload);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(mlServiceUrl + "/enroll", payload, Map.class);
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
        enrollment.setStudent(studentRepo.findById(studentId).get());
        enrollment.setFaceEmbedding(result.get("embedding") != null ? result.get("embedding").toString() : result.get("embeddin").toString());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore((Double) result.getOrDefault("quality", 1.0));

        return enrollmentRepo.save(enrollment);
    }

    private boolean verifyFace(Long studentId, String imageBase64, String storedEmbedding) {
        Map<String, Object> payload = Map.of(
                "studentId", studentId,
                "image", imageBase64,
                "storedEmbedding", storedEmbedding
        );
        LOGGER.info("Sending verification request to ML service: " + payload);
        ResponseEntity<Map> response;
        try {
            response = restTemplate.postForEntity(mlServiceUrl + "/verify", payload, Map.class);
        } catch (RestClientException e) {
            LOGGER.severe("ML service connection failed: " + e.getMessage());
            throw new RuntimeException("ML service connection failed: " + e.getMessage());
        }

        if (!response.getStatusCode().is2xxSuccessful()) {
            LOGGER.severe("ML verification failed: HTTP " + response.getStatusCode());
            throw new RuntimeException("ML verification failed: HTTP " + response.getStatusCode());
        }

        Map<String, Object> result = response.getBody();
        LOGGER.info("ML service verification response: " + (result != null ? result : "null"));
        if (result == null) {
            LOGGER.severe("Verification failed: Null ML service response");
            return false;
        }
        return Boolean.TRUE.equals(result.get("match")) && Boolean.TRUE.equals(result.get("liveness"));
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

    @Override
    public UserDetails loadUserByUsername(String userName) throws UsernameNotFoundException {
        Student student = studentRepo.findByUserName(userName);
        if (student == null) {
            throw new UsernameNotFoundException("User not found: " + userName);
        }
        LOGGER.info("Loading user: " + userName + " with role: " + student.getRole().name());
        return org.springframework.security.core.userdetails.User.builder()
                .username(student.getUserName())
                .password(student.getPassword())
                .authorities("ROLE_" + student.getRole().name())
                .build();
    }
}