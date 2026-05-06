package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.user.OAuth2User;
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

    public Map<String, Object> handleGoogleOAuthLogin(OAuth2User oauth2User) {
        String email = oauth2User.getAttribute("email");
        String firstName = oauth2User.getAttribute("given_name");
        String lastName = oauth2User.getAttribute("family_name");
        String fullName = oauth2User.getAttribute("name");

        if (email == null || email.isBlank()) {
            throw new RuntimeException("Google account email is missing");
        }

        Student student = studentRepo.findByEmail(email);

        if (student == null) {
            student = new Student();
            student.setEmail(email);
            student.setUserName(generateUniqueUsernameFromEmail(email));
            student.setPassword(passwordEncoder.encode("GOOGLE_OAUTH_USER"));
            student.setRole(Enums.UserRole.STUDENT);
            student.setFirstName(firstName != null ? firstName : fullName);
            student.setLastName(lastName != null ? lastName : "");
            student.setIsActive(true);
            student = studentRepo.save(student);
        }

        final UserDetails userDetails = loadUserByUsername(student.getUserName());
        final String jwt = jwtUtil.generateToken(userDetails);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("userId", student.getId());
        response.put("userName", student.getUserName());
        response.put("email", student.getEmail());
        response.put("role", "ROLE_" + student.getRole().name());
        response.put("verified", true);
        response.put("activationAllowed", true);

        return response;
    }

    private String generateUniqueUsernameFromEmail(String email) {
        String base = email.split("@")[0].replaceAll("[^a-zA-Z0-9._-]", "");
        if (base.isBlank()) {
            base = "google_user";
        }

        String candidate = base;
        int suffix = 1;
        while (studentRepo.findByUserName(candidate) != null) {
            candidate = base + suffix;
            suffix++;
        }

        return candidate;
    }

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
            @SuppressWarnings("unchecked")
            List<String> audioSamples = (List<String>) payload.get("audio");
            if (audioSamples != null && !audioSamples.isEmpty()) {
                LOGGER.info("Calling ML service for voice enrollment with " + audioSamples.size() + " sample(s)");
                enrollVoice(student.getId(), audioSamples);
            } else {
                LOGGER.warning("No audio samples provided during registration — voice enrollment skipped");
            }
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
        String audioBase64 = (String) payload.get("audio");

        LOGGER.info("Logging in user: " + userName);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userName, password)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final UserDetails userDetails = loadUserByUsername(userName);
        final String jwt = jwtUtil.generateToken(userDetails);

        Student student = studentRepo.findByUserName(userName);
        
        boolean verified = true;
        if (student.getRole() == Enums.UserRole.STUDENT && imageBase64 != null) {
            Enrollment enrollment = enrollmentRepo.findByStudentId(student.getId());
            if (enrollment != null && enrollment.getFaceEmbedding() != null) {
                verified = verifyFace(student.getId(), imageBase64, enrollment.getFaceEmbedding(), audioBase64);
                if (!verified) {
                    throw new RuntimeException("Biometric verification failed");
                }
                LOGGER.info("Biometric verification successful for user " + userName);
            } else {
                LOGGER.warning("User " + userName + " has no enrollment data. Skipping biometric verification.");
            }
        } else {
            LOGGER.info("User " + userName + " authenticated via password. Biometric verification skipped for login.");
        }

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("userId", student.getId());
        response.put("userName", userName);
        response.put("email", student.getEmail());
        response.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        response.put("verified", verified);
        response.put("activationAllowed", true);

        return response;
    }

    // Simple login without face verification
    public Map<String, Object> simpleLogin(String userName, String password) {
        LOGGER.info("Simple login (no face verification) for user: " + userName);

        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(userName, password)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final UserDetails userDetails = loadUserByUsername(userName);
        final String jwt = jwtUtil.generateToken(userDetails);

        Student student = studentRepo.findByUserName(userName);

        Map<String, Object> response = new HashMap<>();
        response.put("token", jwt);
        response.put("userId", student.getId());
        response.put("userName", userName);
        response.put("email", student.getEmail());
        response.put("role", userDetails.getAuthorities().iterator().next().getAuthority());
        response.put("verified", false); // No face verification performed
        response.put("activationAllowed", true);
        response.put("message", "Login successful. Please enroll your face for enhanced security.");

        return response;
    }

    private void enrollVoice(Long studentId, List<String> audioSamples) {
        boolean enrolled = false;
        String lastError = "No valid audio samples provided";

        for (String audioSample : audioSamples) {
            try {
                Map<String, Object> payload = new HashMap<>();
                payload.put("studentId", studentId);
                payload.put("audio", audioSample);
                LOGGER.info("Sending voice enrollment sample to ML service");
                ResponseEntity<Map> response = restTemplate.postForEntity(mlServiceUrl + "/voice/enroll", payload, Map.class);

                if (response.getStatusCode().is2xxSuccessful()) {
                    Map<String, Object> result = response.getBody();
                    if (result != null && Boolean.TRUE.equals(result.get("success"))) {
                        LOGGER.info("Voice enrollment succeeded for student " + studentId);
                        enrolled = true;
                        break; // First successful sample is enough
                    } else {
                        lastError = result != null ? String.valueOf(result.get("error")) : "Unknown ML error";
                        LOGGER.warning("Voice sample rejected by ML: " + lastError);
                    }
                } else {
                    lastError = "ML service HTTP " + response.getStatusCode();
                    LOGGER.warning(lastError);
                }
            } catch (RestClientException e) {
                lastError = "ML voice service connection failed: " + e.getMessage();
                LOGGER.severe(lastError);
            }
        }

        if (!enrolled) {
            throw new RuntimeException("Voice enrollment failed — " + lastError);
        }
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

    private boolean verifyFace(Long studentId, String imageBase64, String storedEmbedding, String audioBase64) {
        Map<String, Object> payload = new java.util.HashMap<>();
        payload.put("studentId", studentId);
        payload.put("image", imageBase64);
        payload.put("storedEmbedding", storedEmbedding);
        if (audioBase64 != null && !audioBase64.isEmpty()) {
            payload.put("audio", audioBase64);
        }
        
        LOGGER.info("Sending verification request to ML service: " + payload.keySet());
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
        
        boolean faceMatch = Boolean.TRUE.equals(result.get("match"));
        boolean liveness = Boolean.TRUE.equals(result.get("liveness"));
        boolean voiceMatch = Boolean.TRUE.equals(result.get("voice"));
        
        LOGGER.info("Verification results - Face: " + faceMatch + ", Liveness: " + liveness + ", Voice: " + voiceMatch);
        
        // All checks required for full verification
        return faceMatch && liveness && voiceMatch;
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
                .authorities(new SimpleGrantedAuthority("ROLE_" + student.getRole().name()))
                .build();
    }
}