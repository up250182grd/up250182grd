package com.englishos.support;

import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.englishos.support.TestInfrastructure.CapturingEmailSender;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/** Base de las pruebas de integración: aplicación completa en un puerto real + MySQL en Testcontainers. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
@Import(TestInfrastructure.class)
public abstract class IntegrationTest {

    protected static final String ADMIN_EMAIL = "admin@englishos.test";
    protected static final String ADMIN_PASSWORD = "Owner-Secret-Phrase-2026";
    protected static final String STRONG_PASSWORD = "Correct-Horse-Battery-42";

    private static final Pattern TOKEN = Pattern.compile("token=([A-Za-z0-9_%-]+)");

    @LocalServerPort
    protected int port;

    @Autowired
    protected CapturingEmailSender mailbox;

    protected ApiClient newClient() {
        return new ApiClient(port);
    }

    protected static String uniqueEmail() {
        return "student-" + UUID.randomUUID() + "@example.com";
    }

    protected ApiClient.Response register(ApiClient client, String email, String password) {
        return client.post("/api/v1/auth/register", Map.of(
                "email", email,
                "password", password,
                "displayName", "Ana Estudiante",
                "acceptTerms", true,
                "acceptPrivacy", true));
    }

    protected String tokenFromLastEmail(String email) {
        Matcher m = TOKEN.matcher(mailbox.lastTo(email).textBody());
        assertThat(m.find()).as("el correo contiene un enlace con token").isTrue();
        return java.net.URLDecoder.decode(m.group(1), java.nio.charset.StandardCharsets.UTF_8);
    }

    /** Registra, verifica y deja la sesión iniciada. */
    protected ApiClient verifiedStudent(String email) {
        ApiClient client = newClient();
        assertThat(register(client, email, STRONG_PASSWORD).status()).isEqualTo(201);
        assertThat(client.post("/api/v1/auth/verify-email", Map.of("token", tokenFromLastEmail(email))).status())
                .isEqualTo(200);
        assertThat(client.login(email, STRONG_PASSWORD).status()).isEqualTo(200);
        return client;
    }

    protected ApiClient admin() {
        ApiClient client = newClient();
        assertThat(client.login(ADMIN_EMAIL, ADMIN_PASSWORD).status()).isEqualTo(200);
        return client;
    }
}
