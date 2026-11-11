package com.englishos.identity.domain;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.TreeSet;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.englishos.shared.ids.Ulid;

@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, unique = true, length = 26, updatable = false)
    private String publicId;

    @Column(nullable = false, unique = true, length = 254)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private UserStatus status;

    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<Role> roles = new HashSet<>();

    protected User() {
    }

    public static User register(String normalizedEmail, String passwordHash, Role initialRole, Instant now) {
        User user = new User();
        user.publicId = Ulid.next();
        user.email = normalizedEmail;
        user.passwordHash = passwordHash;
        user.status = UserStatus.ACTIVE;
        user.createdAt = now;
        user.updatedAt = now;
        user.roles.add(initialRole);
        return user;
    }

    // ---- Reglas de acceso -------------------------------------------------------------------

    public boolean isLocked(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }

    public boolean canLogIn() {
        return status == UserStatus.ACTIVE;
    }

    /** Registra un intento fallido y bloquea temporalmente tras {@code maxFailures} intentos. */
    public void recordFailedLogin(int maxFailures, Duration lockDuration, Instant now) {
        failedLoginCount++;
        if (failedLoginCount >= maxFailures) {
            lockedUntil = now.plus(lockDuration);
            failedLoginCount = 0;
        }
        updatedAt = now;
    }

    public void recordSuccessfulLogin(Instant now) {
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = now;
        updatedAt = now;
    }

    public void markEmailVerified(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
            updatedAt = now;
        }
    }

    public void changePasswordHash(String newHash, Instant now) {
        passwordHash = newHash;
        failedLoginCount = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    public void suspend(Instant now) {
        status = UserStatus.SUSPENDED;
        updatedAt = now;
    }

    /**
     * Cierre de cuenta: se quitan los datos que identifican a la persona y el acceso. La fila se conserva (anónima)
     * porque los movimientos contables y los cobros, que la ley obliga a guardar, apuntan a ella.
     */
    public void anonymize(String unusablePasswordHash, Instant now) {
        email = "eliminado-" + publicId.toLowerCase(java.util.Locale.ROOT) + "@cuenta-eliminada.invalid";
        emailVerifiedAt = null;
        passwordHash = unusablePasswordHash;
        status = UserStatus.DELETED;
        failedLoginCount = 0;
        lockedUntil = null;
        lastLoginAt = null;
        roles.clear();
        updatedAt = now;
    }

    public void reactivate(Instant now) {
        status = UserStatus.ACTIVE;
        updatedAt = now;
    }

    public void replaceRoles(Set<Role> newRoles, Instant now) {
        roles.clear();
        roles.addAll(newRoles);
        updatedAt = now;
    }

    public boolean hasRole(RoleCode code) {
        return roles.stream().anyMatch(r -> r.getCode() == code);
    }

    public Set<String> roleCodes() {
        Set<String> codes = new TreeSet<>();
        roles.forEach(r -> codes.add(r.getCode().name()));
        return codes;
    }

    public Set<String> permissionCodes() {
        Set<String> codes = new TreeSet<>();
        roles.forEach(r -> r.getPermissions().forEach(p -> codes.add(p.getCode())));
        return codes;
    }

    // ---- Accesores --------------------------------------------------------------------------

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getEmail() {
        return email;
    }

    public Instant getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    public boolean isEmailVerified() {
        return emailVerifiedAt != null;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public UserStatus getStatus() {
        return status;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Set<Role> getRoles() {
        return Set.copyOf(roles);
    }
}
