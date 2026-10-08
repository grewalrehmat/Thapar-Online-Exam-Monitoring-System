package com.javalab.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "submissions",
       uniqueConstraints = @UniqueConstraint(columnNames = {"lab_id", "student_id"}))
public class Submission {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private LabSession lab;

    @ManyToOne(optional = false)
    private User student;

    @Lob
    @Column(nullable = false)
    private String sourceCode = "";

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubmissionStatus status = SubmissionStatus.DRAFT;

    private Integer score;

    @Column(length = 4000)
    private String feedback;

    private String output;
    private String errorOutput;
    private Integer exitCode;
    private Long executionTimeMs;
    private LocalDateTime lastSavedAt;
    private LocalDateTime submittedAt;

    public Submission() {}

    public Submission(LabSession lab, User student) {
        this.lab = lab;
        this.student = student;
        this.lastSavedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public LabSession getLab() { return lab; }
    public User getStudent() { return student; }
    public String getSourceCode() { return sourceCode; }
    public SubmissionStatus getStatus() { return status; }
    public Integer getScore() { return score; }
    public String getFeedback() { return feedback; }
    public String getOutput() { return output; }
    public String getErrorOutput() { return errorOutput; }
    public Integer getExitCode() { return exitCode; }
    public Long getExecutionTimeMs() { return executionTimeMs; }
    public LocalDateTime getLastSavedAt() { return lastSavedAt; }
    public LocalDateTime getSubmittedAt() { return submittedAt; }

    public void setSourceCode(String sourceCode) { this.sourceCode = sourceCode; }
    public void setStatus(SubmissionStatus status) { this.status = status; }
    public void setScore(Integer score) { this.score = score; }
    public void setFeedback(String feedback) { this.feedback = feedback; }
    public void setOutput(String output) { this.output = output; }
    public void setErrorOutput(String errorOutput) { this.errorOutput = errorOutput; }
    public void setExitCode(Integer exitCode) { this.exitCode = exitCode; }
    public void setExecutionTimeMs(Long executionTimeMs) { this.executionTimeMs = executionTimeMs; }
    public void setLastSavedAt(LocalDateTime lastSavedAt) { this.lastSavedAt = lastSavedAt; }
    public void setSubmittedAt(LocalDateTime submittedAt) { this.submittedAt = submittedAt; }
}
