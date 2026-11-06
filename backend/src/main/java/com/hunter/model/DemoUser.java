package com.hunter.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "hunter_users")
public class DemoUser {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 80)
    private String username;

    @Column(nullable = false, length = 140)
    private String displayName;

    @Column(length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 40)
    private String role;

    @Column(nullable = false, length = 100)
    private String workspaceName;

    @Column(nullable = false)
    private Instant createdAt;

    protected DemoUser() {
    }

    public DemoUser(String id, String username, String displayName, String role, String workspaceName, Instant createdAt) {
        this(id, username, displayName, null, role, workspaceName, createdAt);
    }

    public DemoUser(String id, String username, String displayName, String passwordHash,
                    String role, String workspaceName, Instant createdAt) {
        this.id = id;
        this.username = username;
        this.displayName = displayName;
        this.passwordHash = passwordHash;
        this.role = role;
        this.workspaceName = workspaceName;
        this.createdAt = createdAt;
    }

    public String getId() { return id; }
    public String getUsername() { return username; }
    public String getDisplayName() { return displayName; }
    public String getPasswordHash() { return passwordHash; }
    public String getRole() { return role; }
    public String getWorkspaceName() { return workspaceName; }
    public Instant getCreatedAt() { return createdAt; }
}
