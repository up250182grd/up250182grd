package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;

import com.englishos.config.AppProperties;
import com.englishos.identity.application.IdentityEmails.VerificationEmailRequested;
import com.englishos.identity.domain.AuthToken;
import com.englishos.identity.domain.Consent;
import com.englishos.identity.domain.EmailAddress;
import com.englishos.identity.domain.PasswordPolicy;
import com.englishos.identity.domain.Role;
import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.User;
import com.englishos.identity.domain.UserProfile;
import com.englishos.identity.infrastructure.AuthTokenRepository;
import com.englishos.identity.infrastructure.ConsentRepository;
import com.englishos.identity.infrastructure.RoleRepository;
import com.englishos.identity.infrastructure.UserProfileRepository;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Registro de estudiantes y verificación de correo (RF-IDN-1, RF-IDN-2). */
@Service
public class RegistrationService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final RoleRepository roles;
    private final ConsentRepository consents;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    RegistrationService(UserRepository users, UserProfileRepository profiles, RoleRepository roles,
            ConsentRepository consents, AuthTokenRepository tokens, PasswordEncoder passwordEncoder,
            ApplicationEventPublisher events, AppProperties properties, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.roles = roles;
        this.consents = consents;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    public record RegisterCommand(String email, String password, String displayName, boolean acceptTerms,
            boolean acceptPrivacy) {
    }

    @Transactional
    public User registerStudent(RegisterCommand cmd) {
        if (!cmd.acceptTerms() || !cmd.acceptPrivacy()) {
            throw ApiException.badRequest("CONSENT_REQUIRED",
                    "Debes aceptar los términos de uso y el aviso de privacidad.");
        }
        String email = EmailAddress.normalize(cmd.email());
        PasswordPolicy.validate(cmd.password(), email).ifPresent(msg -> {
            throw ApiException.badRequest("WEAK_PASSWORD", msg);
        });
        if (users.existsByEmail(email)) {
            // Revela que el correo existe; es aceptable en registro (alternativa: flujo "revisa tu correo").
            // Se mitiga con rate limiting en Fase 9.
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.");
        }
        Instant now = Instant.now(clock);
        User user = createUser(email, cmd.password(), cmd.displayName().trim(), RoleCode.STUDENT, now);

        consents.save(Consent.accept(user.getId(), Consent.DocumentType.TERMS, properties.legal().termsVersion(), now));
        consents.save(Consent.accept(user.getId(), Consent.DocumentType.PRIVACY, properties.legal().privacyVersion(), now));

        // Usuario nuevo: no tiene tokens pendientes que invalidar.
        issueVerificationToken(user, cmd.displayName().trim(), now, false);
        return user;
    }

    /** Usado también por el bootstrap del administrador. */
    @Transactional
    User createUser(String normalizedEmail, String rawPassword, String displayName, RoleCode roleCode, Instant now) {
        Role role = roles.findByCode(roleCode)
                .orElseThrow(() -> new IllegalStateException("Rol no sembrado: " + roleCode));
        User user = User.register(normalizedEmail, passwordEncoder.encode(rawPassword), role, now);
        try {
            users.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            throw ApiException.conflict("EMAIL_TAKEN", "Ya existe una cuenta con ese correo.");
        }
        profiles.save(UserProfile.createDefault(user.getId(), displayName, now));
        return user;
    }

    @Transactional
    public void verifyEmail(String rawToken) {
        Instant now = Instant.now(clock);
        AuthToken token = tokens.findByHashForUpdate(OneTimeTokens.hash(rawToken))
                .filter(t -> t.getPurpose() == AuthToken.Purpose.EMAIL_VERIFY)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido."));
        if (!token.isUsable(now)) {
            throw ApiException.gone("TOKEN_EXPIRED", "El enlace ya se usó o caducó. Solicita uno nuevo.");
        }
        token.markUsed(now);
        User user = users.findById(token.getUserId()).orElseThrow();
        user.markEmailVerified(now);
    }

    /** Reenvía la verificación. Respuesta idéntica exista o no el correo (no revela cuentas). */
    @Transactional
    public void resendVerification(String rawEmail) {
        String email = EmailAddress.normalize(rawEmail);
        users.findByEmail(email)
                .filter(u -> !u.isEmailVerified() && u.canLogIn())
                .ifPresent(u -> {
                    String name = profiles.findById(u.getId()).map(UserProfile::getDisplayName).orElse("");
                    issueVerificationToken(u, name, Instant.now(clock), true);
                });
    }

    private void issueVerificationToken(User user, String displayName, Instant now, boolean invalidatePrevious) {
        if (invalidatePrevious) {
            tokens.invalidatePending(user.getId(), AuthToken.Purpose.EMAIL_VERIFY, now);
        }
        String raw = OneTimeTokens.newToken();
        tokens.save(AuthToken.issue(user.getId(), AuthToken.Purpose.EMAIL_VERIFY, OneTimeTokens.hash(raw),
                now.plus(properties.security().emailVerificationTtl()), now));
        events.publishEvent(new VerificationEmailRequested(user.getEmail(), displayName, raw));
    }
}
