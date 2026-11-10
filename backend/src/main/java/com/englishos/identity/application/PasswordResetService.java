package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;

import com.englishos.config.AppProperties;
import com.englishos.identity.application.IdentityEmails.PasswordResetEmailRequested;
import com.englishos.identity.domain.AuthToken;
import com.englishos.identity.domain.EmailAddress;
import com.englishos.identity.domain.PasswordPolicy;
import com.englishos.identity.domain.User;
import com.englishos.identity.infrastructure.AuthTokenRepository;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Recuperación de contraseña sin revelar si el correo existe (RF-IDN-4). */
@Service
public class PasswordResetService {

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final SessionRevoker sessionRevoker;
    private final ApplicationEventPublisher events;
    private final AppProperties properties;
    private final Clock clock;

    PasswordResetService(UserRepository users, AuthTokenRepository tokens, PasswordEncoder passwordEncoder,
            SessionRevoker sessionRevoker, ApplicationEventPublisher events, AppProperties properties, Clock clock) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.sessionRevoker = sessionRevoker;
        this.events = events;
        this.properties = properties;
        this.clock = clock;
    }

    @Transactional
    public void requestReset(String rawEmail) {
        Instant now = Instant.now(clock);
        users.findByEmail(EmailAddress.normalize(rawEmail))
                .filter(User::canLogIn)
                .ifPresent(user -> {
                    tokens.invalidatePending(user.getId(), AuthToken.Purpose.PASSWORD_RESET, now);
                    String raw = OneTimeTokens.newToken();
                    tokens.save(AuthToken.issue(user.getId(), AuthToken.Purpose.PASSWORD_RESET,
                            OneTimeTokens.hash(raw), now.plus(properties.security().passwordResetTtl()), now));
                    events.publishEvent(new PasswordResetEmailRequested(user.getEmail(), raw));
                });
    }

    @Transactional
    public void confirmReset(String rawToken, String newPassword) {
        Instant now = Instant.now(clock);
        AuthToken token = tokens.findByHashForUpdate(OneTimeTokens.hash(rawToken))
                .filter(t -> t.getPurpose() == AuthToken.Purpose.PASSWORD_RESET)
                .orElseThrow(() -> ApiException.badRequest("INVALID_TOKEN", "El enlace no es válido."));
        if (!token.isUsable(now)) {
            throw ApiException.gone("TOKEN_EXPIRED", "El enlace ya se usó o caducó. Solicita uno nuevo.");
        }
        User user = users.findById(token.getUserId()).orElseThrow();
        PasswordPolicy.validate(newPassword, user.getEmail()).ifPresent(msg -> {
            throw ApiException.badRequest("WEAK_PASSWORD", msg);
        });
        token.markUsed(now);
        user.changePasswordHash(passwordEncoder.encode(newPassword), now);
        // Quien abre el enlace demuestra controlar el correo: se considera verificado.
        user.markEmailVerified(now);
        // Cierra todas las sesiones abiertas con la contraseña anterior.
        sessionRevoker.revokeAll(user.getEmail());
    }
}
