package com.javalab.account;
import jakarta.persistence.*;
@Entity
@Table(name = "course")
public class Course {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
    @Column(nullable = false, unique = true, length = 30) private String code;
    @Column(nullable = false, length = 140) private String name;
    @Column(nullable = false) private boolean active = true;
    protected Course() {}
    public Course(String code, String name) { this.code = code; this.name = name; }
    public Long getId() { return id; }
    public String getCode() { return code; }
    public String getName() { return name; }
    public boolean isActive() { return active; }
}
