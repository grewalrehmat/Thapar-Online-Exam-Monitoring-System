package com.javalab.labs;
import com.javalab.account.StudyGroup;
import jakarta.persistence.*;
import java.time.Instant;
@Entity
@Table(name = "lab_group_assignment", uniqueConstraints = @UniqueConstraint(name = "uk_lab_group", columnNames = {"lab_id", "group_id"}))
public class LabGroupAssignment {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "lab_id", nullable = false) private LabSession lab;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "group_id", nullable = false) private StudyGroup group;
    @Column(name = "assigned_at", nullable = false, updatable = false) private Instant assignedAt = Instant.now();
    protected LabGroupAssignment() {}
    public LabGroupAssignment(LabSession lab, StudyGroup group){this.lab=lab;this.group=group;}
    public Long getId(){return id;} public LabSession getLab(){return lab;} public StudyGroup getGroup(){return group;}
}
