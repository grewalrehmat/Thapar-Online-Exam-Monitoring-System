package com.javalab.controller;

import com.javalab.model.*;
import com.javalab.repository.*;
import com.javalab.service.CodeRunnerService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class ApiController {

    private final UserRepository users;
    private final LabRepository labs;
    private final SubmissionRepository submissions;
    private final ActivityRepository activities;
    private final CodeRunnerService runner;

    public ApiController(UserRepository users, LabRepository labs,
                         SubmissionRepository submissions, ActivityRepository activities,
                         CodeRunnerService runner) {
        this.users = users;
        this.labs = labs;
        this.submissions = submissions;
        this.activities = activities;
        this.runner = runner;
    }

    private User currentUser(Authentication auth) {
        return users.findByEmail(auth.getName()).orElseThrow();
    }

    @PostMapping("/labs/{labId}/draft")
    public ResponseEntity<?> saveDraft(@PathVariable Long labId,
                                       @RequestBody Map<String, String> body,
                                       Authentication auth) {
        User user = currentUser(auth);
        LabSession lab = labs.findById(labId).orElseThrow();

        Submission s = submissions.findByLabIdAndStudentId(labId, user.getId())
                .orElseGet(() -> new Submission(lab, user));

        if (s.getStatus() == SubmissionStatus.SUBMITTED) {
            return ResponseEntity.badRequest().body(Map.of("error", "Submission is locked"));
        }

        s.setSourceCode(body.getOrDefault("code", ""));
        s.setLastSavedAt(LocalDateTime.now());
        submissions.save(s);

        activities.save(new ActivityEvent(user, lab, ActivityType.DRAFT_SAVE, null));

        return ResponseEntity.ok(Map.of("status", "saved"));
    }

    @PostMapping("/labs/{labId}/submit")
    public ResponseEntity<?> submit(@PathVariable Long labId,
                                    @RequestBody Map<String, String> body,
                                    Authentication auth) {
        User user = currentUser(auth);
        LabSession lab = labs.findById(labId).orElseThrow();

        Submission s = submissions.findByLabIdAndStudentId(labId, user.getId())
                .orElseGet(() -> new Submission(lab, user));

        s.setSourceCode(body.getOrDefault("code", ""));
        s.setStatus(SubmissionStatus.SUBMITTED);
        s.setSubmittedAt(LocalDateTime.now());
        s.setLastSavedAt(LocalDateTime.now());
        submissions.save(s);

        activities.save(new ActivityEvent(user, lab, ActivityType.SUBMIT, null));

        return ResponseEntity.ok(Map.of("status", "submitted"));
    }

    @PostMapping("/labs/{labId}/run")
    public ResponseEntity<?> run(@PathVariable Long labId,
                                 @RequestBody Map<String, String> body,
                                 Authentication auth) {
        User user = currentUser(auth);
        LabSession lab = labs.findById(labId).orElseThrow();

        Submission s = submissions.findByLabIdAndStudentId(labId, user.getId())
                .orElseGet(() -> new Submission(lab, user));

        String code = body.getOrDefault("code", "");
        s.setSourceCode(code);
        s.setStatus(SubmissionStatus.RUNNING);
        submissions.save(s);

        var result = runner.run(code);

        s.setStatus(result.exitCode() == 0 ? SubmissionStatus.DRAFT : SubmissionStatus.FAILED);
        s.setOutput(result.stdout());
        s.setErrorOutput(result.stderr());
        s.setExitCode(result.exitCode());
        s.setExecutionTimeMs(result.executionTimeMs());
        s.setLastSavedAt(LocalDateTime.now());
        submissions.save(s);

        activities.save(new ActivityEvent(user, lab, ActivityType.RUN, null));

        return ResponseEntity.ok(Map.of(
                "stdout", result.stdout(),
                "stderr", result.stderr(),
                "exitCode", result.exitCode(),
                "executionTimeMs", result.executionTimeMs()
        ));
    }

    @PostMapping("/labs/{labId}/activity")
    public ResponseEntity<?> activity(@PathVariable Long labId,
                                      @RequestBody Map<String, String> body,
                                      Authentication auth) {
        User user = currentUser(auth);
        LabSession lab = labs.findById(labId).orElseThrow();

        ActivityType type;
        try {
            type = ActivityType.valueOf(body.getOrDefault("type", "DRAFT_SAVE"));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "Unknown event"));
        }

        activities.save(new ActivityEvent(user, lab, type, body.get("metadata")));
        return ResponseEntity.ok(Map.of("status", "recorded"));
    }
}
