package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;

import com.englishos.config.AppProperties;
import com.englishos.identity.domain.EmailAddress;
import com.englishos.identity.domain.PasswordPolicy;
import com.englishos.identity.domain.RoleCode;
import com.englishos.identity.domain.User;
import com.englishos.identity.infrastructure.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crea el primer administrador a partir de ADMIN_BOOTSTRAP_EMAIL / ADMIN_BOOTSTRAP_PASSWORD, solo si no existe
 * ningún administrador. No hay ningún endpoint público que otorgue el rol ADMIN.
 */
@Component
class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);

    private final UserRepository users;
    private final RegistrationService registration;
    private final AppProperties properties;
    private final Clock clock;

    AdminBootstrap(UserRepository users, RegistrationService registration, AppProperties properties, Clock clock) {
        this.users = users;
        this.registration = registration;
        this.properties = properties;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        AppProperties.AdminBootstrap cfg = properties.adminBootstrap();
        if (!cfg.isConfigured() || users.existsActiveWithRole(RoleCode.ADMIN)) {
            return;
        }
        String email = EmailAddress.normalize(cfg.email());
        PasswordPolicy.validate(cfg.password(), email).ifPresent(msg -> {
            throw new IllegalStateException("ADMIN_BOOTSTRAP_PASSWORD no cumple la política: " + msg);
        });
        if (users.existsByEmail(email)) {
            throw new IllegalStateException(
                    "ADMIN_BOOTSTRAP_EMAIL pertenece a una cuenta existente; asigna el rol manualmente.");
        }
        Instant now = Instant.now(clock);
        String name = cfg.displayName() == null || cfg.displayName().isBlank() ? "Administrador" : cfg.displayName();
        User admin = registration.createUser(email, cfg.password(), name, RoleCode.ADMIN, now);
        admin.markEmailVerified(now);
        log.info("Administrador inicial creado (id público {}). Elimina ADMIN_BOOTSTRAP_PASSWORD del entorno.",
                admin.getPublicId());
    }
}
