package com.example.anti_cheating_backend.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.example.anti_cheating_backend.dto.AvailableExamDTO;
import com.example.anti_cheating_backend.entity.Enrollment;
import com.example.anti_cheating_backend.entity.Enums;
import com.example.anti_cheating_backend.entity.Exam;
import com.example.anti_cheating_backend.entity.ExamSession;
import com.example.anti_cheating_backend.repo.EnrollmentRepo;
import com.example.anti_cheating_backend.repo.ExamRepo;
import com.example.anti_cheating_backend.repo.ExamSessionRepo;

@Service
public class ExamService {

    @Autowired
    private ExamRepo examRepo;
    
    @Autowired
    private ExamSessionRepo examSessionRepo;
    
    @Autowired
    private EnrollmentRepo enrollmentRepo;

    // Get all exams
    public List<Exam> getAllExams() {
        return examRepo.findAll();
    }

    // Get exam by ID
    public Exam getExamById(Long examId) {
        return examRepo.findById(examId)
                .orElseThrow(() -> new RuntimeException("Exam not found with id: " + examId));
    }

    // Get published exams (available for student enrollment)
    public List<Exam> getPublishedExams() {
        return examRepo.findByStatusIn(Arrays.asList(
            Enums.ExamStatus.PUBLISHED,
            Enums.ExamStatus.ONGOING
        ));
    }

    // Get ongoing exams
    public List<Exam> getOngoingExams() {
        return examRepo.findByStatus(Enums.ExamStatus.ONGOING);
    }

    // Create exam
    public Exam createExam(Exam exam) {
        exam.setCreatedAt(LocalDateTime.now());
        if (exam.getStatus() == null) {
            exam.setStatus(Enums.ExamStatus.DRAFT);
        }
        return examRepo.save(exam);
    }

    // Update exam
    public Exam updateExam(Long examId, Exam examDetails) {
        Exam exam = getExamById(examId);
        
        if (examDetails.getTitle() != null) {
            exam.setTitle(examDetails.getTitle());
        }
        if (examDetails.getDescription() != null) {
            exam.setDescription(examDetails.getDescription());
        }
        if (examDetails.getStartDate() != null) {
            exam.setStartDate(examDetails.getStartDate());
        }
        if (examDetails.getEndDate() != null) {
            exam.setEndDate(examDetails.getEndDate());
        }
        if (examDetails.getStatus() != null) {
            exam.setStatus(examDetails.getStatus());
        }
        if (examDetails.getMaxStudents() != null) {
            exam.setMaxStudents(examDetails.getMaxStudents());
        }
        if (examDetails.getPassingScore() != null) {
            exam.setPassingScore(examDetails.getPassingScore());
        }
        
        return examRepo.save(exam);
    }

    // Delete exam
    public void deleteExam(Long examId) {
        try {
            Exam exam = getExamById(examId);
            
            // Log deletion attempt
            System.out.println("Attempting to delete exam ID: " + examId + " - Title: " + exam.getTitle());
            
            // Check for related data
            if (exam.getTests() != null) {
                System.out.println("  - Tests to cascade delete: " + exam.getTests().size());
            }
            if (exam.getEnrollments() != null) {
                System.out.println("  - Enrollments to cascade delete: " + exam.getEnrollments().size());
            }
            
            // Delete the exam (cascade will handle tests and enrollments)
            examRepo.delete(exam);
            
            System.out.println("✅ Exam " + examId + " deleted successfully");
        } catch (Exception e) {
            System.err.println("❌ Error deleting exam " + examId + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to delete exam: " + e.getMessage());
        }
    }

    // Publish exam (make it available for students)
    public Exam publishExam(Long examId) {
        Exam exam = getExamById(examId);
        exam.setStatus(Enums.ExamStatus.PUBLISHED);
        return examRepo.save(exam);
    }

    // Start exam
    public Exam startExam(Long examId) {
        Exam exam = getExamById(examId);
        exam.setStatus(Enums.ExamStatus.ONGOING);
        if (exam.getStartDate() == null) {
            exam.setStartDate(LocalDateTime.now());
        }
        return examRepo.save(exam);
    }

    // Complete exam
    public Exam completeExam(Long examId) {
        Exam exam = getExamById(examId);
        exam.setStatus(Enums.ExamStatus.COMPLETED);
        if (exam.getEndDate() == null) {
            exam.setEndDate(LocalDateTime.now());
        }
        return examRepo.save(exam);
    }

    // Archive exam
    public Exam archiveExam(Long examId) {
        Exam exam = getExamById(examId);
        exam.setStatus(Enums.ExamStatus.ARCHIVED);
        return examRepo.save(exam);
    }
    
    /**
     * Get available exams for a student with session information
     * Returns published or ongoing exams with their active sessions
     */
    public List<AvailableExamDTO> getAvailableExamsForStudent(Long studentId) {
        // Get published and ongoing exams
        List<Enums.ExamStatus> activeStatuses = List.of(
            Enums.ExamStatus.PUBLISHED, 
            Enums.ExamStatus.ONGOING
        );
        List<Exam> exams = examRepo.findByStatusIn(activeStatuses);
        
        // Get active or scheduled exam sessions
        List<ExamSession> activeSessions = examSessionRepo.findByStatus(Enums.SessionStatus.ACTIVE);
        List<ExamSession> scheduledSessions = examSessionRepo.findByStatus(Enums.SessionStatus.SCHEDULED);
        List<ExamSession> allSessions = new ArrayList<>();
        allSessions.addAll(activeSessions);
        allSessions.addAll(scheduledSessions);
        
        // Get student enrollments
        List<Enrollment> enrollments = enrollmentRepo.findAllByStudentId(studentId);
        
        // Build DTO list
        return exams.stream().map(exam -> {
            AvailableExamDTO dto = new AvailableExamDTO(exam);
            
            // Add active sessions for this exam (match by exam name)
            // Note: ExamSession doesn't have direct exam_id, so we match by examName
            List<ExamSession> examSessions = allSessions.stream()
                .filter(session -> session.getExamName() != null && 
                                 session.getExamName().equalsIgnoreCase(exam.getTitle()))
                .collect(Collectors.toList());
            
            examSessions.forEach(dto::addSession);
            
            // Check if student is enrolled
            // Filter out face-only enrollments (exam is null) before checking enrollment
            enrollments.stream()
                .filter(e -> e.getExam() != null && e.getExam().getId().equals(exam.getId()))
                .findFirst()
                .ifPresentOrElse(
                    enrollment -> {
                        dto.setIsEnrolled(true);
                        dto.setEnrollmentStatus(enrollment.getStatus() != null ? 
                            enrollment.getStatus().name() : "UNKNOWN");
                    },
                    () -> {
                        dto.setIsEnrolled(false);
                        dto.setEnrollmentStatus("NOT_ENROLLED");
                    }
                );
            
            return dto;
        }).collect(Collectors.toList());
    }
}
