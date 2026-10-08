package com.javalab.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "lab_sessions")
public class LabSession {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String title;

    @Column(length = 4000)
    private String description;

    @Column(nullable = false)
    private LocalDateTime startsAt;

    @Column(nullable = false)
    private LocalDateTime endsAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LabStatus status;

    @Column(nullable = false)
    private boolean secureMode;

    public LabSession() {}

    public LabSession(String title, String description, LocalDateTime startsAt,
                      LocalDateTime endsAt, LabStatus status, boolean secureMode) {
        this.title = title;
        this.description = description;
        this.startsAt = startsAt;
        this.endsAt = endsAt;
        this.status = status;
        this.secureMode = secureMode;
    }

    public Long getId() { return id; }
    public String getTitle() { return title; }
    public String getDescription() { return description; }
    public LocalDateTime getStartsAt() { return startsAt; }
    public LocalDateTime getEndsAt() { return endsAt; }
    public LabStatus getStatus() { return status; }
    public boolean isSecureMode() { return secureMode; }

    public void setId(Long id) { this.id = id; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setStartsAt(LocalDateTime startsAt) { this.startsAt = startsAt; }
    public void setEndsAt(LocalDateTime endsAt) { this.endsAt = endsAt; }
    public void setStatus(LabStatus status) { this.status = status; }
    public void setSecureMode(boolean secureMode) { this.secureMode = secureMode; }
}
