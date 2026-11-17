package com.javalab.labs;
import com.javalab.account.Course;
import com.javalab.account.UserAccount;
import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;
@Entity
@Table(name = "lab_session")
public class LabSession {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false) private Course course;
    @Column(nullable = false, length = 140) private String title;
    @Column(nullable = false, length = 12000) private String question;
    @Column(name = "scheduled_date", nullable = false) private LocalDate scheduledDate;
    @Column(name = "secure_mode", nullable = false) private boolean secureMode;
    @Column(name = "camera_monitoring", nullable = false) private boolean cameraMonitoring;
    @Enumerated(EnumType.STRING) @Column(nullable = false, length = 20) private LabStatus status;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "created_by", nullable = false) private UserAccount createdBy;
    @Column(name = "created_at", nullable = false, updatable = false) private Instant createdAt = Instant.now();
    @Column(name = "updated_at", nullable = false) private Instant updatedAt = Instant.now();
    protected LabSession() {}
    public LabSession(Course course, String title, String question, LocalDate scheduledDate, boolean secureMode, boolean cameraMonitoring, UserAccount createdBy) {
        this.course=course; this.title=title; this.question=question; this.scheduledDate=scheduledDate;
        this.secureMode=secureMode; this.cameraMonitoring=cameraMonitoring; this.createdBy=createdBy; this.status=LabStatus.UPCOMING;
    }
    public Long getId(){return id;} public Course getCourse(){return course;} public String getTitle(){return title;}
    public String getQuestion(){return question;} public LocalDate getScheduledDate(){return scheduledDate;}
    public boolean isSecureMode(){return secureMode;} public boolean isCameraMonitoring(){return cameraMonitoring;}
    public LabStatus getStatus(){return status;} public UserAccount getCreatedBy(){return createdBy;}
    public Instant getCreatedAt(){return createdAt;} public void setStatus(LabStatus status){this.status=status;this.updatedAt=Instant.now();}
    public void updateDetails(String title,String question,LocalDate date){this.title=title;this.question=question;this.scheduledDate=date;this.updatedAt=Instant.now();}
}
