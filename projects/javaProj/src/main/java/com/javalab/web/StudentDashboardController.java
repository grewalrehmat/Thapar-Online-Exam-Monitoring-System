package com.javalab.web;
import com.javalab.account.StudentEnrollmentRepository;
import com.javalab.labs.LabService;
import com.javalab.account.UserAccount;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/student")
public class StudentDashboardController {
    private final StudentEnrollmentRepository enrollments;
    private final LabService labs;
    public StudentDashboardController(StudentEnrollmentRepository enrollments, LabService labs) { this.enrollments = enrollments; this.labs = labs; }
    @GetMapping public String dashboard(@AuthenticationPrincipal UserAccount student, Model model) {
        model.addAttribute("student", student);
        model.addAttribute("enrollments", enrollments.findByStudentIdOrderByCourseCodeAsc(student.getId()));
        model.addAttribute("labs", labs.studentLabs(student));
        return "student/dashboard";
    }
}
