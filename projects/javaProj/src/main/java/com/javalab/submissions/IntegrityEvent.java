package com.javalab.submissions;

import com.javalab.account.UserAccount;
import com.javalab.labs.LabSession;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "integrity_event", uniqueConstraints = @UniqueConstraint(
        name = "uk_integrity_student_lab_key", columnNames = {"student_id", "lab_id", "idempotency_key"}),
        indexes = {@Index(name = "ix_integrity_lab_student_time", columnList = "lab_id,student_id,occurred_at"),
                @Index(name = "ix_integrity_student_time", columnList = "student_id,occurred_at")})
public class IntegrityEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lab_id", nullable = false) private LabSession lab;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private UserAccount student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submission_id") private Submission submission;
    @Enumerated(EnumType.STRING) @Column(name = "event_type", nullable = false, length = 32) private IntegrityEventType type;
    @Column(name = "idempotency_key", nullable = false, length = 36) private String idempotencyKey;
    @Column(name = "flag_number") private Integer flagNumber;
    @Column(name = "details", length = 256) private String details;
    @Column(name = "occurred_at", nullable = false, updatable = false) private Instant occurredAt = Instant.now();

    protected IntegrityEvent() {}
    public IntegrityEvent(LabSession lab, UserAccount student, Submission submission, IntegrityEventType type,
                          String idempotencyKey, Integer flagNumber) {
        this(lab, student, submission, type, idempotencyKey, flagNumber, null);
    }
    public IntegrityEvent(LabSession lab, UserAccount student, Submission submission, IntegrityEventType type,
                          String idempotencyKey, Integer flagNumber, String details) {
        this.lab = lab;
        this.student = student;
        this.submission = submission;
        this.type = type;
        this.idempotencyKey = idempotencyKey == null ? UUID.randomUUID().toString() : idempotencyKey;
        this.flagNumber = flagNumber;
        this.details = details == null ? null : details.substring(0, Math.min(256, details.length()));
    }
    public Long getId() { return id; }
    public LabSession getLab() { return lab; }
    public UserAccount getStudent() { return student; }
    public Submission getSubmission() { return submission; }
    public IntegrityEventType getType() { return type; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public Integer getFlagNumber() { return flagNumber; }
    public String getDetails() { return details; }
    public Instant getOccurredAt() { return occurredAt; }
}
