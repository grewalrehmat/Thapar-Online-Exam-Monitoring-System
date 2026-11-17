package com.javalab.account;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "teacher_group_assignment", uniqueConstraints = @UniqueConstraint(name = "uk_teacher_course_group", columnNames = {"teacher_id", "course_id", "group_id"}))
public class TeacherGroupAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "teacher_id", nullable = false) private UserAccount teacher;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "course_id", nullable = false) private Course course;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id", nullable = false) private StudyGroup group;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt = Instant.now();
    protected TeacherGroupAssignment() {}
    public TeacherGroupAssignment(UserAccount teacher, Course course, StudyGroup group) { this.teacher = teacher; this.course = course; this.group = group; }
    public Long getId() { return id; }
    public UserAccount getTeacher() { return teacher; }
    public Course getCourse() { return course; }
    public StudyGroup getGroup() { return group; }
}
