package com.example.anti_cheating_backend.service;

import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Student;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.StudentRepo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

@Service
public class EnrollmentService {

    @Autowired
    private EnrollmentRepo enrollmentRepo;
    @Autowired
    private StudentRepo studentRepo;
    @Value("${ml.service.url:http://localhost:5000}")
    private String mlService;

    private final RestTemplate restTemplate = new RestTemplate();

    public Enrollment enroll(Long studentId,String imageBase64){
        Student student = studentRepo.findById(studentId)
                .orElseThrow(() -> new RuntimeException("Student not found: "+studentId));

        //Delete existing if any
        Enrollment existing = enrollmentRepo.findByStudentId(studentId);
        if (existing != null){
            enrollmentRepo.delete(existing);
        }
        Map<String,Object> payload = Map.of("studentId",studentId,"image",imageBase64);
        ResponseEntity<Map> response = restTemplate.postForEntity(mlService +"/enroll",payload, Map.class);

        if(!response.getStatusCode().is2xxSuccessful()){
            throw new RuntimeException("ML enrollment failed : "+ response.getStatusCode());
        }
        Map<String,Object> result = response.getBody();
        if (!Boolean.TRUE.equals(result.get("success"))){
            throw new RuntimeException("Enrollment failed: "+ result.get("Eroor"));
        }
        Enrollment enrollment = new Enrollment();
        enrollment.setStudent(student);
        enrollment.setFaceEmbedding(result.get("embedding").toString());
        enrollment.setEnrollmentDate(LocalDateTime.now());
        enrollment.setIsVerified(true);
        enrollment.setVerificationScore((Double) result.getOrDefault("quality",1.0));

        return enrollmentRepo.save(enrollment);
    }
    public Enrollment getEnrollment(Long studentId){
        return enrollmentRepo.findByStudentId(studentId);
    }
}
