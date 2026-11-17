package com.javalab.account;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "student_enrollment", uniqueConstraints = @UniqueConstraint(name = "uk_student_course", columnNames = {"student_id", "course_id"}))
public class StudentEnrollment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "student_id", nullable = false) private UserAccount student;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false) private Course course;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id", nullable = false) private StudyGroup group;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt = Instant.now();
    protected StudentEnrollment() {}
    public StudentEnrollment(UserAccount student, Course course, StudyGroup group) { this.student = student; this.course = course; this.group = group; }
    public Long getId() { return id; }
    public UserAccount getStudent() { return student; }
    public Course getCourse() { return course; }
    public StudyGroup getGroup() { return group; }
}
