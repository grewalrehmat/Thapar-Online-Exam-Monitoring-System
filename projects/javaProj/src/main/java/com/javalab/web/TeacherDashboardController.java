package com.javalab.web;
import com.javalab.account.TeacherGroupAssignmentRepository;
import com.javalab.account.UserAccount;
import com.javalab.labs.LabService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
@Controller
@RequestMapping("/teacher")
public class TeacherDashboardController {
    private final TeacherGroupAssignmentRepository assignments;
    private final LabService labs;
    public TeacherDashboardController(TeacherGroupAssignmentRepository assignments, LabService labs) { this.assignments = assignments; this.labs = labs; }
    @GetMapping public String dashboard(@AuthenticationPrincipal UserAccount teacher, Model model) {
        model.addAttribute("teacher", teacher);
        model.addAttribute("assignments", assignments.findByTeacherIdOrderByCourseCodeAscGroupNameAsc(teacher.getId()));
        model.addAttribute("labs", labs.teacherLabs(teacher));
        return "teacher/dashboard";
    }
}
