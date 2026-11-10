package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import com.englishos.config.AppProperties;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.domain.EmailAddress;
import com.englishos.identity.domain.User;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Verificación de credenciales con bloqueo temporal tras intentos fallidos (RF-IDN-3).
 * El manejo de la sesión HTTP (cookie, cambio de ID de sesión) lo hace la capa web.
 */
@Service
public class LoginService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AppProperties properties;
    private final Clock clock;
    /** Hash ficticio para igualar el tiempo de respuesta cuando el correo no existe. */
    private final String dummyHash;

    LoginService(UserRepository users, PasswordEncoder passwordEncoder, AppProperties properties, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.properties = properties;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("englishos-dummy-password-for-timing");
    }

    /** Los intentos fallidos deben persistir aunque la solicitud termine en error. */
    @Transactional(noRollbackFor = ApiException.class)
    public CurrentUser authenticate(String rawEmail, String rawPassword) {
        Instant now = Instant.now(clock);
        Optional<User> found = users.findByEmail(EmailAddress.normalize(rawEmail));
        if (found.isEmpty()) {
            passwordEncoder.matches(rawPassword == null ? "" : rawPassword, dummyHash);
            throw invalidCredentials();
        }
        User user = found.get();
        if (user.isLocked(now)) {
            throw new ApiException(HttpStatus.TOO_MANY_REQUESTS, "TOO_MANY_ATTEMPTS",
                    "Demasiados intentos fallidos. Espera unos minutos e inténtalo de nuevo.");
        }
        if (!passwordEncoder.matches(rawPassword == null ? "" : rawPassword, user.getPasswordHash())) {
            AppProperties.Security sec = properties.security();
            user.recordFailedLogin(sec.maxFailedLogins(), sec.lockDuration(), now);
            throw invalidCredentials();
        }
        if (!user.canLogIn()) {
            throw ApiException.forbidden("ACCOUNT_SUSPENDED",
                    "Tu cuenta está suspendida. Contacta al soporte de EnglishOS.");
        }
        user.recordSuccessfulLogin(now);
        return toCurrentUser(user);
    }

    static CurrentUser toCurrentUser(User user) {
        return new CurrentUser(user.getId(), user.getPublicId(), user.getEmail(), user.roleCodes(),
                user.permissionCodes());
    }

    private static ApiException invalidCredentials() {
        return ApiException.unauthorized("INVALID_CREDENTIALS", "Correo o contraseña incorrectos.");
    }
}
