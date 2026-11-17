package com.javalab.monitoring;

import com.javalab.account.UserAccount;
import com.javalab.labs.LabSession;
import com.javalab.submissions.Submission;
import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "monitoring_snapshot", indexes = {
        @Index(name = "ix_snapshot_lab_student_time", columnList = "lab_id,student_id,captured_at")})
public class MonitoringSnapshot {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(name = "snapshot_id", nullable = false, unique = true, length = 36) private String snapshotId;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lab_id", nullable = false) private LabSession lab;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private UserAccount student;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "submission_id") private Submission submission;
    @Column(name = "captured_at", nullable = false) private Instant capturedAt;
    @Column(name = "person_count", nullable = false) private int personCount;
    @Column(name = "phone_count", nullable = false) private int phoneCount;
    @Column(name = "multiple_people", nullable = false) private boolean multiplePeople;
    @Column(name = "phone_detected", nullable = false) private boolean phoneDetected;
    @Column(name = "detections_json", nullable = false, length = 20000) private String detectionsJson;
    @Column(name = "saved_at", nullable = false, updatable = false) private Instant savedAt = Instant.now();

    protected MonitoringSnapshot() {}
    public MonitoringSnapshot(String snapshotId, LabSession lab, UserAccount student, Submission submission,
                              MonitoringAnalysis result, String detectionsJson) {
        this.snapshotId = snapshotId;
        this.lab = lab;
        this.student = student;
        this.submission = submission;
        this.capturedAt = result.timestamp();
        this.personCount = result.personCount();
        this.phoneCount = result.phoneCount();
        this.multiplePeople = result.multiplePeople();
        this.phoneDetected = result.phoneDetected();
        this.detectionsJson = detectionsJson;
    }
    public Long getId() { return id; }
    public String getSnapshotId() { return snapshotId; }
    public LabSession getLab() { return lab; }
    public UserAccount getStudent() { return student; }
    public Submission getSubmission() { return submission; }
    public Instant getCapturedAt() { return capturedAt; }
    public int getPersonCount() { return personCount; }
    public int getPhoneCount() { return phoneCount; }
    public boolean isMultiplePeople() { return multiplePeople; }
    public boolean isPhoneDetected() { return phoneDetected; }
    public String getDetectionsJson() { return detectionsJson; }
}
