package com.ledgercore.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * A single login attempt record used for sliding-window lockout (Requirement 2.9).
 */
@Entity
@Table(name = "login_attempts")
public class LoginAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id", nullable = false, updatable = false)
    private Long id;

    @Column(name = "email_normalized", nullable = false, updatable = false)
    private String emailNormalized;

    @Column(name = "success", nullable = false, updatable = false)
    private boolean success;

    @Column(name = "attempted_at", nullable = false, updatable = false)
    private Instant attemptedAt;

    protected LoginAttempt() {
    }

    public LoginAttempt(String emailNormalized, boolean success, Instant attemptedAt) {
        this.emailNormalized = emailNormalized;
        this.success = success;
        this.attemptedAt = attemptedAt;
    }

    public Long getId() {
        return id;
    }

    public String getEmailNormalized() {
        return emailNormalized;
    }

    public boolean isSuccess() {
        return success;
    }

    public Instant getAttemptedAt() {
        return attemptedAt;
    }
}
