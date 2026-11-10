package com.englishos.identity;

import java.util.Map;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-IDN-1 a RF-IDN-4: registro, verificación, sesión, CSRF, bloqueo y recuperación de contraseña. */
class AuthFlowIT extends IntegrationTest {

    @Test
    void registerVerifyLoginMeLogout() {
        String email = uniqueEmail();
        ApiClient client = newClient();

        var registered = register(client, email, STRONG_PASSWORD);
        assertThat(registered.status()).isEqualTo(201);
        assertThat(registered.json().path("emailVerificationRequired").asBoolean()).isTrue();
        assertThat(registered.json().path("publicId").asString()).hasSize(26);

        // Antes de verificar: puede iniciar sesión pero el correo figura como no verificado.
        assertThat(client.login(email, STRONG_PASSWORD).status()).isEqualTo(200);
        assertThat(client.get("/api/v1/me").json().path("emailVerified").asBoolean()).isFalse();

        var verified = client.post("/api/v1/auth/verify-email", Map.of("token", tokenFromLastEmail(email)));
        assertThat(verified.status()).isEqualTo(200);

        var me = client.get("/api/v1/me");
        assertThat(me.status()).isEqualTo(200);
        assertThat(me.json().path("email").asString()).isEqualTo(email);
        assertThat(me.json().path("emailVerified").asBoolean()).isTrue();
        assertThat(me.json().path("roles").toString()).contains("STUDENT").doesNotContain("ADMIN");
        assertThat(me.json().path("profile").path("explanationLanguage").asString()).isEqualTo("es");

        assertThat(client.post("/api/v1/auth/logout", null).status()).isEqualTo(204);
        assertThat(client.get("/api/v1/me").status()).isEqualTo(401);
    }

    @Test
    void emailIsNormalizedAndDuplicatesRejected() {
        String email = uniqueEmail();
        var created = register(newClient(), email.toUpperCase(), STRONG_PASSWORD);
        assertThat(created.status()).isEqualTo(201);
        assertThat(created.json().path("email").asString()).isEqualTo(email);

        var duplicate = register(newClient(), email, STRONG_PASSWORD);
        assertThat(duplicate.status()).isEqualTo(409);
        assertThat(duplicate.code()).isEqualTo("EMAIL_TAKEN");

        // El inicio de sesión tampoco distingue mayúsculas.
        assertThat(newClient().login(email.toUpperCase(), STRONG_PASSWORD).status()).isEqualTo(200);
    }

    @Test
    void weakPasswordsAndMissingConsentAreRejected() {
        var weak = register(newClient(), uniqueEmail(), "password1234");
        assertThat(weak.status()).isEqualTo(400);
        assertThat(weak.code()).isEqualTo("WEAK_PASSWORD");

        var tooShort = register(newClient(), uniqueEmail(), "short");
        assertThat(tooShort.status()).isEqualTo(400);
        assertThat(tooShort.code()).isEqualTo("VALIDATION_ERROR");
        assertThat(tooShort.json().path("errors").toString()).contains("password");

        var noConsent = newClient().post("/api/v1/auth/register", Map.of(
                "email", uniqueEmail(), "password", STRONG_PASSWORD, "displayName", "Ana",
                "acceptTerms", true, "acceptPrivacy", false));
        assertThat(noConsent.status()).isEqualTo(400);
        assertThat(noConsent.json().path("errors").toString()).contains("acceptPrivacy");
    }

    @Test
    void verificationTokenCannotBeReused() {
        String email = uniqueEmail();
        ApiClient client = newClient();
        register(client, email, STRONG_PASSWORD);
        String token = tokenFromLastEmail(email);

        assertThat(client.post("/api/v1/auth/verify-email", Map.of("token", token)).status()).isEqualTo(200);
        var reused = client.post("/api/v1/auth/verify-email", Map.of("token", token));
        assertThat(reused.status()).isEqualTo(410);
        assertThat(reused.code()).isEqualTo("TOKEN_EXPIRED");

        var bogus = client.post("/api/v1/auth/verify-email", Map.of("token", "no-existe"));
        assertThat(bogus.status()).isEqualTo(400);
        assertThat(bogus.code()).isEqualTo("INVALID_TOKEN");
    }

    @Test
    void resendingVerificationInvalidatesPreviousToken() {
        String email = uniqueEmail();
        ApiClient client = newClient();
        register(client, email, STRONG_PASSWORD);
        String first = tokenFromLastEmail(email);

        assertThat(client.post("/api/v1/auth/resend-verification", Map.of("email", email)).status()).isEqualTo(202);
        String second = tokenFromLastEmail(email);
        assertThat(second).isNotEqualTo(first);

        assertThat(client.post("/api/v1/auth/verify-email", Map.of("token", first)).status()).isEqualTo(410);
        assertThat(client.post("/api/v1/auth/verify-email", Map.of("token", second)).status()).isEqualTo(200);
    }

    @Test
    void stateChangingRequestsWithoutCsrfHeaderAreRejected() {
        ApiClient client = newClient().withoutCsrfHeader();
        var res = client.login(ADMIN_EMAIL, ADMIN_PASSWORD);
        assertThat(res.status()).isEqualTo(403);
        assertThat(res.code()).isEqualTo("CSRF_INVALID");
    }

    @Test
    void wrongCredentialsGiveSameAnswerForUnknownEmail() {
        String email = uniqueEmail();
        register(newClient(), email, STRONG_PASSWORD);

        var wrongPassword = newClient().login(email, "Wrong-Password-1234");
        var unknownEmail = newClient().login(uniqueEmail(), "Wrong-Password-1234");
        assertThat(wrongPassword.status()).isEqualTo(401);
        assertThat(unknownEmail.status()).isEqualTo(401);
        assertThat(wrongPassword.body()).isEqualTo(unknownEmail.body());
    }

    @Test
    void accountIsTemporarilyLockedAfterFiveFailures() {
        String email = uniqueEmail();
        register(newClient(), email, STRONG_PASSWORD);
        ApiClient client = newClient();
        for (int i = 0; i < 5; i++) {
            assertThat(client.login(email, "Wrong-Password-1234").status()).isEqualTo(401);
        }
        var locked = client.login(email, STRONG_PASSWORD);
        assertThat(locked.status()).isEqualTo(429);
        assertThat(locked.code()).isEqualTo("TOO_MANY_ATTEMPTS");
    }

    @Test
    void passwordResetDoesNotRevealAccountsAndRevokesSessions() {
        assertThat(newClient().post("/api/v1/auth/password-reset/request", Map.of("email", uniqueEmail())).status())
                .isEqualTo(202);

        String email = uniqueEmail();
        ApiClient session = verifiedStudent(email);
        assertThat(session.get("/api/v1/me").status()).isEqualTo(200);

        ApiClient other = newClient();
        assertThat(other.post("/api/v1/auth/password-reset/request", Map.of("email", email)).status()).isEqualTo(202);
        String token = tokenFromLastEmail(email);

        var weak = other.post("/api/v1/auth/password-reset/confirm",
                Map.of("token", token, "newPassword", "password1234"));
        assertThat(weak.code()).isEqualTo("WEAK_PASSWORD");

        String newPassword = "Brand-New-Secret-Phrase-7";
        assertThat(other.post("/api/v1/auth/password-reset/confirm",
                Map.of("token", token, "newPassword", newPassword)).status()).isEqualTo(204);

        assertThat(session.get("/api/v1/me").status()).as("la sesión previa se revoca").isEqualTo(401);
        assertThat(newClient().login(email, STRONG_PASSWORD).status()).isEqualTo(401);
        assertThat(newClient().login(email, newPassword).status()).isEqualTo(200);
        assertThat(other.post("/api/v1/auth/password-reset/confirm",
                Map.of("token", token, "newPassword", "Another-Secret-Phrase-8")).status()).isEqualTo(410);
    }

    @Test
    void profileCanBeUpdatedWithValidation() {
        ApiClient client = verifiedStudent(uniqueEmail());

        var ok = client.patch("/api/v1/me/profile", Map.of(
                "displayName", "Ana B.", "explanationLanguage", "en", "dailyGoalMinutes", 30));
        assertThat(ok.status()).isEqualTo(200);
        assertThat(ok.json().path("explanationLanguage").asString()).isEqualTo("en");
        assertThat(ok.json().path("dailyGoalMinutes").asInt()).isEqualTo(30);

        assertThat(client.patch("/api/v1/me/profile", Map.of("explanationLanguage", "fr")).status()).isEqualTo(400);
        assertThat(client.patch("/api/v1/me/profile", Map.of("dailyGoalMinutes", 1)).status()).isEqualTo(400);
        assertThat(client.patch("/api/v1/me/profile", Map.of("timezone", "Mars/Olympus")).code())
                .isEqualTo("INVALID_TIMEZONE");
    }

    @Test
    void protectedEndpointsRequireAuthentication() {
        var res = newClient().get("/api/v1/me");
        assertThat(res.status()).isEqualTo(401);
        assertThat(res.code()).isEqualTo("UNAUTHENTICATED");
    }
}
