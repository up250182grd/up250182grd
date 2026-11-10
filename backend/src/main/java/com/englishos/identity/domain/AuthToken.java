package com.englishos.identity.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Token de un solo uso. Solo se persiste el hash SHA-256; el valor en claro no se guarda nunca. */
@Entity
@Table(name = "auth_tokens")
public class AuthToken {

    public enum Purpose { EMAIL_VERIFY, PASSWORD_RESET }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32, updatable = false)
    private Purpose purpose;

    @Column(name = "token_hash", nullable = false, unique = true, length = 64, updatable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuthToken() {
    }

    public static AuthToken issue(Long userId, Purpose purpose, String tokenHash, Instant expiresAt, Instant now) {
        AuthToken t = new AuthToken();
        t.userId = userId;
        t.purpose = purpose;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.createdAt = now;
        return t;
    }

    public boolean isUsable(Instant now) {
        return usedAt == null && expiresAt.isAfter(now);
    }

    public void markUsed(Instant now) {
        if (usedAt != null) {
            throw new IllegalStateException("El token ya fue utilizado");
        }
        usedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public Purpose getPurpose() {
        return purpose;
    }
}
