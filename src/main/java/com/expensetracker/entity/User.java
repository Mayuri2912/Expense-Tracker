package com.expensetracker.entity;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
public class User implements Serializable {

    private static final long serialVersionUID = 1L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /*
     * Legacy column from an earlier version of the schema. The database still
     * has a NOT NULL "name" column alongside "full_name" - rather than altering
     * the live database, this field is kept in sync automatically (see
     * syncLegacyName()) so inserts/updates never violate the NOT NULL
     * constraint. It is intentionally not exposed on any form; "fullName" is
     * the single source of truth everywhere else in the application.
     */
    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @NotBlank(message = "Full name is required")
    @Size(max = 100, message = "Full name must be under 100 characters")
    @Column(name = "full_name", nullable = false)
    private String fullName;

    @NotBlank(message = "Email is required")
    @Email(message = "Please provide a valid email address")
    @Column(nullable = false, unique = true)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 6, message = "Password must be at least 6 characters")
    @Column(nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    private String password;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    public User() {
    }

    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = LocalDateTime.now();
        }
        syncLegacyName();
    }

    @PreUpdate
    protected void onUpdate() {
        syncLegacyName();
    }

    // Keeps the legacy "name" column mirroring "fullName" so the NOT NULL
    // constraint on the old column is always satisfied without exposing it.
    private void syncLegacyName() {
        if (this.fullName != null) {
            this.name = this.fullName.length() > 100 ? this.fullName.substring(0, 100) : this.fullName;
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
