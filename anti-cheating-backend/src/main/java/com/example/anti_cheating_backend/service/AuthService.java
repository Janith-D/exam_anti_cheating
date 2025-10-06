package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import com.example.anti_cheating_backend.security.JwtUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;


@Service
public class AuthService implements UserDetailsService {

    @Autowired
    private StudentRepo studentRepo;

    @Autowired
    private EnrollmentRepo enrollmentRepo;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private AuthenticationManager authenticationManager;

    @Value("${ml.service.url:http://localhost:5000}")
    private String mlService;

    private final RestTemplate restTemplate = new RestTemplate();

    //register student
    public Map<String, Object> register(Map<String,Object> payload){
        String username = (String) payload.get("userName");
        String email = (String) payload.get("email");
        String password = (String) payload.get("password");
        String imageBase64 = (String) payload.get("image");

        if (studentRepo.findByUserName(username) != null){
            throw new RuntimeException("Username already exists");
        }

        Student student = new Student();
        student.setUserName(username);
        student.setEmail(email);
        student.setPassword(passwordEncoder.encode(password));
        student.setRole(Enums.UserRole.STUDENT);
        student.setIsActive(true);
        student.setCreatedAt(LocalDateTime.now());
        student.setUpdatedAt(LocalDateTime.now());
        student = studentRepo.save(student);

        //Enroll face
        Enrollment enrollment = enrollFace(student.getId(),imageBase64);
        if (enrollment == null){
            throw new RuntimeException("Face enrollment failed during register");
        }
        Map<String,Object> response = new HashMap<>();
        response.put("message","Register and enrollment successfully");
        response.put("userId",student.getId());
        response.put("enrollmentId",enrollment.getId());

        return response;
    }

    //Login Student
    public Map<String,Object> login(Map<String,Object> payload){
        String username = (String) payload.get("username");
        String password = (String) payload.get("password");
        String imageBase64 = (String) payload.get("image");

        //Authenticate credentials
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(username,password)
        );
        SecurityContextHolder.getContext().setAuthentication(authentication);
        final UserDetails userDetails = loadUserByUsername(username);
        final String jwt = jwtUtil.generateToken(userDetails);

        //verify face
        Student student = studentRepo.findByUserName(username);
        Enrollment enrollment = enrollmentRepo.findByStudentId(student.getId());
        if(enrollment == null){
            throw new RuntimeException("No enrollment found for user");
        }
        boolean verified = verifyFace(student.getId(),imageBase64,enrollment.getFaceEmbedding());
        if (!verified){
            throw new RuntimeException("Face verification failed during login");
        }
        Map<String,Object> response = new HashMap<>();
        response.put("token",jwt);
        response.put("username",username);
        response.put("role",userDetails.getAuthorities().iterator().next().getAuthority());
        response.put("verified",true);
        response.put("activationAllowed",true);//for extension

        return response;
    }
    private Enrollment enrollFace(Long studentId,String imageBase64){
        Map<String,Object> payload = Map.of("studentId",studentId,"image",imageBase64);
        ResponseEntity<Map> response = restTemplate.postForEntity(mlService + "/enroll",payload, Map.class);

        if (!response.getStatusCode().is2xxSuccessful()){
            throw new RuntimeException("ML enrollment failed : "+ response.getStatusCode());
        }
        Map<String,Object> result = response.getBody();
        if(!Boolean.TRUE.equals(result.get("success"))){
            throw new RuntimeException("Enrollment failed: "+ result.get("error"));
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(studentRepo.findById(studentId).get());
        enrollment.setFaceEmbedding(result.get("embedding").toString());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore((Double) result.getOrDefault("quality",1.0));

        return enrollmentRepo.save(enrollment);

    }

    private boolean verifyFace(Long studentId,String imageBase64, String storedEmbedding){
        Map<String,Object> payload = Map.of(
                "studentId",studentId,
                "image",imageBase64,
                "storeEmbedding",storedEmbedding
        );
        ResponseEntity<Map> response = restTemplate.postForEntity(mlService +"/verify",payload,Map.class);

        if (!response.getStatusCode().is2xxSuccessful()){
            throw new RuntimeException("ML verification failed"+ response.getStatusCode());
        }
        Map<String,Object> result = response.getBody();
        return Boolean.TRUE.equals(result.get("match")) && Boolean.TRUE.equals(result.get("liveness"));
    }
    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Student student = studentRepo.findByUserName(username);
        if (student == null){
            throw new UsernameNotFoundException("User not found: "+username);
        }
        return User.builder()
                .username(student.getUserName())
                .password(student.getPassword())
                .authorities(student.getRole().name())
                .build();

    }
}
