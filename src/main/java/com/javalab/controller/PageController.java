package com.javalab.controller;

import com.javalab.repository.LabRepository;
import com.javalab.repository.SubmissionRepository;
import com.javalab.repository.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class PageController {

    private final UserRepository users;
    private final LabRepository labs;
    private final SubmissionRepository submissions;

    public PageController(UserRepository users, LabRepository labs, SubmissionRepository submissions) {
        this.users = users;
        this.labs = labs;
        this.submissions = submissions;
    }

    @GetMapping("/login")
    String login() {
        return "login";
    }

    @GetMapping("/dashboard")
    String dashboard(Authentication auth, Model model) {
        var user = users.findByEmail(auth.getName()).orElseThrow();
        model.addAttribute("user", user);
        model.addAttribute("labs", labs.findAllByOrderByStartsAtDesc());

        if (user.getRole().name().equals("STUDENT")) {
            model.addAttribute("submissions", submissions.findByStudentId(user.getId()));
            return "student-dashboard";
        }

        return "teacher-dashboard";
    }

    @GetMapping("/lab/{id}")
    String lab(@PathVariable Long id, Authentication auth, Model model) {
        var user = users.findByEmail(auth.getName()).orElseThrow();
        var lab = labs.findById(id).orElseThrow();
        var submission = submissions.findByLabIdAndStudentId(id, user.getId()).orElse(null);

        model.addAttribute("user", user);
        model.addAttribute("lab", lab);
        model.addAttribute("submission", submission);
        return "lab";
    }
}
