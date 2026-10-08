package com.javalab.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "activity_events")
public class ActivityEvent {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private User student;

    @ManyToOne(optional = false)
    private LabSession lab;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ActivityType type;

    @Column(nullable = false)
    private LocalDateTime timestamp;

    private String metadata;

    public ActivityEvent() {}

    public ActivityEvent(User student, LabSession lab, ActivityType type, String metadata) {
        this.student = student;
        this.lab = lab;
        this.type = type;
        this.metadata = metadata;
        this.timestamp = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public User getStudent() { return student; }
    public LabSession getLab() { return lab; }
    public ActivityType getType() { return type; }
    public LocalDateTime getTimestamp() { return timestamp; }
    public String getMetadata() { return metadata; }
}
