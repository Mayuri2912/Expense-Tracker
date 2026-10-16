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

    @NotBlank(message = "Username is required")
    @Column(nullable = false, unique = true)
    private String username;

    // Keep the columns used by the existing MySQL schema populated while the
    // application uses username as its canonical display/login name.
    @Column(name = "name", nullable = false, length = 100)
    private String name;

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

    @Column(name = "api_key", unique = true, length = 64)
    private String apiKey;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;

    public User() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
        this.name = username;
        this.fullName = username;
    }

    // Kept as a view/template alias while the application uses username internally.
    public String getFullName() {
        return fullName != null && !fullName.isBlank() ? fullName : username;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
        this.name = fullName;
        this.username = fullName;
    }

    @PostLoad
    private void populateUsernameFromLegacyName() {
        if (username == null || username.isBlank()) {
            username = fullName != null && !fullName.isBlank() ? fullName : name;
        }
    }

    @PrePersist
    @PreUpdate
    private void synchronizeNameColumns() {
        if (username != null && !username.isBlank()) {
            name = username;
            fullName = username;
        }
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

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
