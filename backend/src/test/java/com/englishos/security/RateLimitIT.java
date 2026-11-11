package com.englishos.security;

import java.util.Map;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * RNF-16: límites por IP (rutas públicas) y por usuario (rutas con sesión), con 429 y Retry-After. Las reglas de la
 * configuración base se combinan con estas: registro e inicio de sesión se suben para poder crear usuarios.
 */
@TestPropertySource(properties = {
        "app.rate-limit.enabled=true",
        "app.rate-limit.rules.register.limit=1000",
        "app.rate-limit.rules.login.limit=1000",
        "app.rate-limit.rules.verify-email.limit=3",
        "app.rate-limit.rules.verify-email.window=1h",
        "app.rate-limit.rules.tutor-start.limit=2",
        "app.rate-limit.rules.tutor-start.window=1h",
})
class RateLimitIT extends IntegrationTest {

    @Test
    void publicEndpointsAreLimitedPerIpWith429AndRetryAfter() {
        ApiClient c = newClient();
        int before = 0;
        int limited = 0;
        for (int i = 0; i < 6; i++) {
            var r = c.post("/api/v1/auth/verify-email", Map.of("token", "token-invalido-" + i));
            if (r.status() == 429) {
                limited++;
                assertThat(r.code()).isEqualTo("RATE_LIMITED");
                assertThat(r.json().path("detail").asString()).contains("Espera");
            } else {
                before++;
            }
        }
        // Las primeras peticiones de la ventana pasan (y fallan por token inválido); el resto recibe 429.
        assertThat(before).isLessThanOrEqualTo(3);
        assertThat(limited).isGreaterThanOrEqualTo(3);

        // La respuesta indica cuándo reintentar (sin sesión ni CSRF: el límite actúa antes que ambos).
        try {
            var res = java.net.http.HttpClient.newHttpClient().send(java.net.http.HttpRequest
                            .newBuilder(java.net.URI.create("http://localhost:" + port + "/api/v1/auth/verify-email"))
                            .header("Content-Type", "application/json")
                            .POST(java.net.http.HttpRequest.BodyPublishers.ofString("{\"token\":\"x\"}")).build(),
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            assertThat(res.statusCode()).isEqualTo(429);
            assertThat(res.headers().firstValue("Retry-After")).hasValueSatisfying(
                    v -> assertThat(Long.parseLong(v)).isBetween(1L, 3600L));
        } catch (java.io.IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void aiEndpointsAreLimitedPerUserNotGlobally() {
        ApiClient a = verifiedStudent(uniqueEmail());
        ApiClient b = verifiedStudent(uniqueEmail());
        assertThat(a.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).status()).isNotEqualTo(429);
        assertThat(a.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).status()).isNotEqualTo(429);
        var third = a.post("/api/v1/tutor/conversations", Map.of("mode", "FREE"));
        assertThat(third.status()).isEqualTo(429);
        assertThat(third.code()).isEqualTo("RATE_LIMITED");
        // Otro usuario desde la misma IP no se ve afectado.
        assertThat(b.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).status()).isNotEqualTo(429);
    }
}
