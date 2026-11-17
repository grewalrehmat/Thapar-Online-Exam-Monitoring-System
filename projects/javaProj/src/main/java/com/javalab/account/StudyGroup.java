package com.javalab.account;
import jakarta.persistence.*;
@Entity
@Table(name = "study_group")
public class StudyGroup {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 40) private String name;
    @Column(nullable = false) private boolean active = true;
    protected StudyGroup() {}
    public StudyGroup(String name) { this.name = name; }
    public Long getId() { return id; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
