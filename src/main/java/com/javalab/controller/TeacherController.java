package com.javalab.controller;

import com.javalab.repository.*;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/teacher")
public class TeacherController {

    private final LabRepository labs;
    private final SubmissionRepository submissions;
    private final ActivityRepository activities;
    private final UserRepository users;

    public TeacherController(
            LabRepository labs,
            SubmissionRepository submissions,
            ActivityRepository activities,
            UserRepository users) {

        this.labs = labs;
        this.submissions = submissions;
        this.activities = activities;
        this.users = users;
    }


    // ========================================================
    // MAIN TEACHER MONITOR
    // ========================================================

    @GetMapping("/lab/{id}")
    String monitor(
            @PathVariable Long id,
            Model model) {

        var lab =
                labs.findById(id)
                        .orElseThrow();

        var students =
                users.findAll()
                        .stream()
                        .filter(user ->
                                user.getRole().name()
                                        .equals("STUDENT"))
                        .sorted(
                                java.util.Comparator
                                        .comparing(
                                                user -> user.getRollNumber() == null
                                                        ? ""
                                                        : user.getRollNumber()
                                        )
                        )
                        .toList();

        var submissionList =
                submissions.findByLabId(id);

        Map<Long, com.javalab.model.Submission> submissionByStudent =
                submissionList.stream()
                        .collect(Collectors.toMap(
                                s -> s.getStudent().getId(),
                                Function.identity(),
                                (a, b) -> b
                        ));

        model.addAttribute("lab", lab);
        model.addAttribute("students", students);
        model.addAttribute("submissions", submissionList);
        model.addAttribute("submissionByStudent", submissionByStudent);

        model.addAttribute(
                "activities",
                activities.findTop100ByLabIdOrderByTimestampDesc(id)
        );

        return "teacher-monitor";
    }


    // ========================================================
    // INDIVIDUAL STUDENT MONITOR
    // ========================================================

    @GetMapping("/lab/{labId}/student/{studentId}")
    String studentMonitor(
            @PathVariable Long labId,
            @PathVariable Long studentId,
            Model model) {

        var lab =
                labs.findById(labId)
                        .orElseThrow();

        var student =
                users.findById(studentId)
                        .orElseThrow();

        var submission =
                submissions
                        .findByLabIdAndStudentId(
                                labId,
                                studentId
                        )
                        .orElse(null);

        var studentActivities =
                activities
                        .findByStudentIdAndLabIdOrderByTimestampDesc(
                                studentId,
                                labId
                        );

        model.addAttribute("lab", lab);
        model.addAttribute("student", student);
        model.addAttribute("submission", submission);
        model.addAttribute("activities", studentActivities);

        return "student-monitor";
    }


    // ========================================================
    // GRADING
    // ========================================================

    @PostMapping("/submission/{id}/grade")
    String grade(
            @PathVariable Long id,
            @RequestParam Integer score,
            @RequestParam(required = false) String feedback) {

        var submission =
                submissions.findById(id)
                        .orElseThrow();

        submission.setScore(
                Math.max(
                        0,
                        Math.min(100, score)
                )
        );

        submission.setFeedback(feedback);

        submissions.save(submission);

        return "redirect:/teacher/lab/" +
                submission.getLab().getId();
    }
}
