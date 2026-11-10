package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.domain.Role;
import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.User;
import com.englishos.identity.domain.UserProfile;
import com.englishos.identity.domain.UserStatus;
import com.englishos.identity.infrastructure.RoleRepository;
import com.englishos.identity.infrastructure.UserProfileRepository;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión de usuarios por el administrador (RF-ADM-1, RF-IDN-8). Los permisos se verifican aquí, en el backend,
 * además de la regla por URL: nunca se confía en lo que muestre la interfaz.
 */
@Service
public class AdminUserService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RoleRepository roles;
    private final SessionRevoker sessionRevoker;
    private final AuditService audit;
    private final Clock clock;

    AdminUserService(UserRepository users, UserProfileRepository profiles, RoleRepository roles,
            SessionRevoker sessionRevoker, AuditService audit, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.roles = roles;
        this.sessionRevoker = sessionRevoker;
        this.audit = audit;
        this.clock = clock;
    }

    public record AdminUserView(String publicId, String email, String displayName, String status,
            boolean emailVerified, Set<String> roles, Instant createdAt, Instant lastLoginAt) {
    }

    @PreAuthorize("hasAuthority('user:read:any')")
    @Transactional(readOnly = true)
    public Page<AdminUserView> search(String query, UserStatus status, Pageable pageable) {
        String q = query == null || query.isBlank() ? null : query.trim().toLowerCase();
        return users.search(q, status, pageable).map(this::toView);
    }

    @PreAuthorize("hasAuthority('user:suspend')")
    @Transactional
    public AdminUserView suspend(CurrentUser actor, String publicId) {
        User user = find(publicId);
        if (user.getId().equals(actor.id())) {
            throw ApiException.badRequest("CANNOT_SUSPEND_SELF", "No puedes suspender tu propia cuenta.");
        }
        UserStatus before = user.getStatus();
        user.suspend(Instant.now(clock));
        audit.record(actor.id(), "USER_SUSPENDED", "USER", user.getPublicId(),
                Map.of("status", before.name()), Map.of("status", user.getStatus().name()));
        sessionRevoker.revokeAll(user.getEmail());
        return toView(user);
    }

    @PreAuthorize("hasAuthority('user:suspend')")
    @Transactional
    public AdminUserView reactivate(CurrentUser actor, String publicId) {
        User user = find(publicId);
        UserStatus before = user.getStatus();
        user.reactivate(Instant.now(clock));
        audit.record(actor.id(), "USER_REACTIVATED", "USER", user.getPublicId(),
                Map.of("status", before.name()), Map.of("status", user.getStatus().name()));
        return toView(user);
    }

    @PreAuthorize("hasAuthority('user:role:write')")
    @Transactional
    public AdminUserView changeRoles(CurrentUser actor, String publicId, Set<RoleCode> newRoles) {
        if (newRoles == null || newRoles.isEmpty()) {
            throw ApiException.badRequest("ROLES_REQUIRED", "El usuario debe tener al menos un rol.");
        }
        User user = find(publicId);
        if (user.getId().equals(actor.id()) && !newRoles.contains(RoleCode.ADMIN)) {
            throw ApiException.badRequest("CANNOT_DEMOTE_SELF", "No puedes quitarte el rol de administrador.");
        }
        Set<String> before = user.roleCodes();
        Set<Role> resolved = newRoles.stream()
                .map(code -> roles.findByCode(code).orElseThrow())
                .collect(Collectors.toSet());
        user.replaceRoles(resolved, Instant.now(clock));
        audit.record(actor.id(), "USER_ROLES_CHANGED", "USER", user.getPublicId(),
                Map.of("roles", before), Map.of("roles", user.roleCodes()));
        // Los permisos viven en la sesión: se revocan para que el cambio surta efecto de inmediato.
        sessionRevoker.revokeAll(user.getEmail());
        return toView(user);
    }

    private User find(String publicId) {
        return users.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("USER_NOT_FOUND", "Usuario no encontrado."));
    }

    private AdminUserView toView(User u) {
        String name = profiles.findById(u.getId()).map(UserProfile::getDisplayName).orElse("");
        return new AdminUserView(u.getPublicId(), u.getEmail(), name, u.getStatus().name(), u.isEmailVerified(),
                u.roleCodes(), u.getCreatedAt(), u.getLastLoginAt());
    }
}
