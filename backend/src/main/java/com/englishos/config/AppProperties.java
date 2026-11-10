package com.englishos.config;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Propiedades propias de EnglishOS (prefijo {@code app}). */
@Validated
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        @NotBlank String baseUrl,
        List<String> corsAllowedOrigins,
        @Valid @NotNull Mail mail,
        @Valid @NotNull Security security,
        @Valid @NotNull Legal legal,
        @Valid @NotNull AdminBootstrap adminBootstrap) {

    public AppProperties {
        corsAllowedOrigins = corsAllowedOrigins == null
                ? List.of()
                : corsAllowedOrigins.stream().filter(o -> o != null && !o.isBlank()).toList();
    }

    public record Mail(@NotBlank String from) {
    }

    public record Security(
            @Min(1) int maxFailedLogins,
            @NotNull Duration lockDuration,
            @NotNull Duration emailVerificationTtl,
            @NotNull Duration passwordResetTtl) {
    }

    public record Legal(@NotBlank String termsVersion, @NotBlank String privacyVersion) {
    }

    /** Crea el primer administrador si no existe ninguno. Vacío = desactivado. */
    public record AdminBootstrap(String email, String password, String displayName) {

        public boolean isConfigured() {
            return email != null && !email.isBlank() && password != null && !password.isBlank();
        }
    }
}
