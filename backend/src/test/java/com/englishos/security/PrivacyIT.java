package com.englishos.security;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englishos.config.RetentionJob;
import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** Derechos del titular (acceso y cancelación) y retención de transcripciones (RNF-11). */
class PrivacyIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    RetentionJob retention;

    private Long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    /** Alumno con saldo, una conversación cerrada con mensajes y errores. */
    private ApiClient studentWithActivity(String email) {
        ApiClient c = verifiedStudent(email);
        c.post("/api/v1/me/wallet/simulated-topups", Map.of("amount", new BigDecimal("100.00"),
                "idempotencyKey", UUID.randomUUID().toString()));
        String conv = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE", "topic", "mi perro Firulais"))
                .json().path("id").asString();
        c.postSse("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "Hello! She have a cat."));
        c.post("/api/v1/tutor/conversations/" + conv + "/close", null);
        return c;
    }

    @Test
    void studentsCanDownloadAllTheirDataWithoutSecrets() {
        String email = uniqueEmail();
        ApiClient c = studentWithActivity(email);
        var res = c.exchange("GET", "/api/v1/me/data-export", null);
        assertThat(res.statusCode()).isEqualTo(200);
        assertThat(res.headers().firstValue("Content-Disposition")).hasValueSatisfying(
                v -> assertThat(v).contains("attachment").contains("mis-datos-englishos.json"));
        JsonNode data = c.get("/api/v1/me/data-export").json();
        assertThat(data.path("identidad").path("cuenta").get(0).path("email").asString()).isEqualTo(email);
        assertThat(data.path("profesor_texto").path("mensajes").size()).isGreaterThanOrEqualTo(2);
        assertThat(data.path("profesor_texto").path("errores").size()).isPositive();
        assertThat(data.path("billetera").path("movimientos").size()).isPositive();
        assertThat(data.has("aprendizaje")).isTrue();
        assertThat(data.has("vocabulario")).isTrue();
        assertThat(data.has("voz")).isTrue();
        assertThat(data.has("logros")).isTrue();
        String raw = res.body().toLowerCase();
        assertThat(raw).doesNotContain("password", "token_hash", "argon");
    }

    @Test
    void deletingTheAccountErasesPersonalContentKeepsAccountingAndRevokesAccess() {
        String email = uniqueEmail();
        ApiClient c = studentWithActivity(email);
        Long uid = userId(email);
        ApiClient secondDevice = newClient();
        assertThat(secondDevice.login(email, STRONG_PASSWORD).status()).isEqualTo(200);

        assertThat(c.post("/api/v1/me/account/delete", Map.of("password", STRONG_PASSWORD, "confirmation", "borrar"))
                .code()).isEqualTo("CONFIRMATION_REQUIRED");
        assertThat(c.post("/api/v1/me/account/delete", Map.of("password", "Wrong-Password-99", "confirmation", "ELIMINAR"))
                .code()).isEqualTo("INVALID_PASSWORD");
        var withBalance = c.post("/api/v1/me/account/delete", Map.of("password", STRONG_PASSWORD, "confirmation", "ELIMINAR"));
        assertThat(withBalance.code()).isEqualTo("BALANCE_NOT_ZERO");

        var ok = c.post("/api/v1/me/account/delete",
                Map.of("password", STRONG_PASSWORD, "confirmation", "ELIMINAR", "acceptBalanceLoss", true));
        assertThat(ok.status()).as(ok.body()).isEqualTo(200);
        assertThat(ok.json().path("deleted").asBoolean()).isTrue();

        // Sin acceso: ni esta sesión, ni la de otro dispositivo, ni volver a entrar.
        assertThat(c.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(secondDevice.get("/api/v1/me").status()).isEqualTo(401);
        assertThat(newClient().login(email, STRONG_PASSWORD).status()).isEqualTo(401);

        // Contenido personal borrado.
        for (String sql : List.of(
                "select count(*) from learner_errors where user_id = ?",
                "select count(*) from ai_messages m join ai_conversations c on c.id = m.conversation_id where c.user_id = ?",
                "select count(*) from ai_conversations where user_id = ? and topic is not null",
                "select count(*) from user_vocabulary_cards where user_id = ?",
                "select count(*) from auth_tokens where user_id = ?",
                "select count(*) from user_roles where user_id = ?")) {
            assertThat(jdbc.queryForObject(sql, Integer.class, uid)).as(sql).isZero();
        }
        Map<String, Object> user = jdbc.queryForMap("select email, status from users where id = ?", uid);
        assertThat(user.get("email").toString()).endsWith("@cuenta-eliminada.invalid");
        assertThat(user.get("status")).isEqualTo("DELETED");
        assertThat(jdbc.queryForObject("select display_name from user_profiles where user_id = ?", String.class, uid))
                .isEqualTo("Cuenta eliminada");
        // Registros contables conservados (obligación legal) y el libro sigue cuadrando.
        assertThat(jdbc.queryForObject("select count(*) from ledger_transactions where user_id = ?", Integer.class, uid))
                .isPositive();
        assertThat(jdbc.queryForObject("select count(*) from usage_settlements where user_id = ?", Integer.class, uid))
                .isPositive();
        assertThat(admin().get("/api/v1/admin/billing/summary").json().path("ledgerProblems").size()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from audit_logs where action = 'ACCOUNT_DELETED' and actor_user_id = ?",
                Integer.class, uid)).isEqualTo(1);
        // El correo queda libre para registrarse de nuevo.
        assertThat(register(newClient(), email, STRONG_PASSWORD).status()).isEqualTo(201);
    }

    @Test
    void theOnlyAdminCannotDeleteTheirAccount() {
        // Otras pruebas ascienden usuarios a administrador: se suspenden temporalmente para que el administrador
        // inicial sea el único activo (si no, el cierre procedería y borraría la cuenta compartida de pruebas).
        String others = """
                select u.id from users u join user_roles ur on ur.user_id = u.id join roles r on r.id = ur.role_id
                where r.code = 'ADMIN' and u.status = 'ACTIVE' and u.email <> ?""";
        List<Long> otherAdmins = jdbc.queryForList(others, Long.class, ADMIN_EMAIL);
        otherAdmins.forEach(id -> jdbc.update("update users set status = 'SUSPENDED' where id = ?", id));
        try {
            var r = admin().post("/api/v1/me/account/delete",
                    Map.of("password", ADMIN_PASSWORD, "confirmation", "ELIMINAR", "acceptBalanceLoss", true));
            assertThat(r.code()).isEqualTo("LAST_ADMIN");
            assertThat(admin().get("/api/v1/me").status()).isEqualTo(200);
        } finally {
            otherAdmins.forEach(id -> jdbc.update("update users set status = 'ACTIVE' where id = ?", id));
        }
    }

    @Test
    void oldTranscriptsArePurgedByTheRetentionJob() {
        String email = uniqueEmail();
        studentWithActivity(email);
        Long uid = userId(email);
        String countSql = "select count(*) from ai_messages m join ai_conversations c on c.id = m.conversation_id where c.user_id = ?";
        assertThat(jdbc.queryForObject(countSql, Integer.class, uid)).isPositive();
        retention.run();
        assertThat(jdbc.queryForObject(countSql, Integer.class, uid)).as("aún dentro del plazo").isPositive();

        jdbc.update("update ai_conversations set closed_at = ? where user_id = ?",
                java.sql.Timestamp.from(Instant.now().minus(200, ChronoUnit.DAYS)), uid);
        assertThat(retention.run()).isPositive();
        assertThat(jdbc.queryForObject(countSql, Integer.class, uid)).isZero();
    }
}
