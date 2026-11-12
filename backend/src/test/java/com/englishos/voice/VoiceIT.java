package com.englishos.voice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 5 de punta a punta contra MySQL real con el proveedor de voz simulado (un "turno" cada 0.3 s): reserva previa,
 * consumo informado por el canal lateral del servidor, cortes automáticos y liquidación ×1.30 / ×1.00.
 */
class VoiceIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private void topup(ApiClient c, String amount) {
        var res = c.post("/api/v1/me/wallet/simulated-topups",
                Map.of("amount", new BigDecimal(amount), "idempotencyKey", UUID.randomUUID().toString()));
        assertThat(res.status()).as(res.body()).isEqualTo(200);
    }

    private JsonNode startCall(ApiClient c) {
        var res = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE", "topic", "my weekend"));
        assertThat(res.status()).as(res.body()).isEqualTo(201);
        return res.json();
    }

    /** Espera (enviando latidos como lo haría la interfaz) hasta que la llamada cumpla la condición. */
    private JsonNode await(ApiClient c, String id, Duration max, boolean heartbeat, Predicate<JsonNode> until) {
        Instant deadline = Instant.now().plus(max);
        JsonNode view = null;
        while (Instant.now().isBefore(deadline)) {
            if (heartbeat) {
                c.post("/api/v1/voice/sessions/" + id + "/heartbeat", null);
            }
            view = c.get("/api/v1/voice/sessions/" + id).json();
            if (until.test(view)) {
                return view;
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError("La llamada no llegó al estado esperado: " + view);
    }

    private static boolean ended(JsonNode v) {
        return "ENDED".equals(v.path("status").asString()) && !v.path("settlement").isMissingNode()
                && !v.path("settlement").isNull();
    }

    private BigDecimal providerCost(String publicId) {
        return jdbc.queryForObject("""
                select coalesce(sum(u.provider_cost), 0) from ai_usage_records u
                join voice_sessions s on s.id = u.subject_id and u.subject_type = 'VOICE_SESSION'
                where s.public_id = ?""", BigDecimal.class, publicId);
    }

    private void assertLedgerClean() {
        var problems = admin().get("/api/v1/admin/billing/summary").json().path("ledgerProblems");
        assertThat(problems.size()).as(problems.toString()).isZero();
    }

    @Test
    void quoteExplainsCostBeforeCallingAndRequiresBalance() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode q = c.get("/api/v1/voice/quote").json();
        assertThat(q.path("canStart").asBoolean()).isFalse();
        assertThat(q.path("model").path("simulated").asBoolean()).isTrue();
        assertThat(q.path("estimatedPerMinuteMxn").decimalValue()).isPositive();
        var res = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE"));
        assertThat(res.status()).isEqualTo(402);
        assertThat(res.code()).isEqualTo("INSUFFICIENT_FUNDS");

        topup(c, "100.00");
        q = c.get("/api/v1/voice/quote").json();
        assertThat(q.path("canStart").asBoolean()).isTrue();
        assertThat(q.path("maxMinutes").asInt()).isEqualTo(15);
    }

    @Test
    void callsCannotStartWithoutATranscriptionTariff() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00");
        jdbc.update("update ai_models set is_enabled = false where model_code = 'fake-transcribe-1'");
        try {
            var res = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE"));
            assertThat(res.status()).isEqualTo(503);
            assertThat(res.code()).isEqualTo("AI_NOT_CONFIGURED");
            assertThat(c.get("/api/v1/me/wallet").json().path("reserved").decimalValue()).isEqualByComparingTo("0.00");
        } finally {
            jdbc.update("update ai_models set is_enabled = true where model_code = 'fake-transcribe-1'");
        }
    }

    @Test
    void unverifiedUsersCannotCall() {
        ApiClient c = newClient();
        String email = uniqueEmail();
        register(c, email, STRONG_PASSWORD);
        c.login(email, STRONG_PASSWORD);
        var res = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE"));
        assertThat(res.status()).isEqualTo(403);
        assertThat(res.code()).isEqualTo("EMAIL_NOT_VERIFIED");
    }

    @Test
    void studentCallIsMeteredByTheServerAndChargedAtCostPlus30Percent() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00");
        JsonNode started = startCall(c);
        String id = started.path("sessionId").asString();
        assertThat(started.path("simulated").asBoolean()).isTrue();
        assertThat(started.path("reservedMxn").decimalValue()).isPositive();
        BigDecimal reserved = started.path("reservedMxn").decimalValue();
        assertThat(c.get("/api/v1/me/wallet").json().path("reserved").decimalValue()).isEqualByComparingTo(reserved);

        // Una sola llamada activa por usuario.
        var second = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE"));
        assertThat(second.status()).isEqualTo(409);
        assertThat(second.code()).isEqualTo("CALL_IN_PROGRESS");

        JsonNode live = await(c, id, Duration.ofSeconds(10), true,
                v -> v.path("measured").path("totalMxn").decimalValue().signum() > 0
                        && v.path("transcript").size() >= 4);
        assertThat(live.path("status").asString()).isEqualTo("ACTIVE");

        JsonNode afterEnd = c.post("/api/v1/voice/sessions/" + id + "/end", null).json();
        assertThat(afterEnd.path("status").asString()).isIn("ENDING", "ENDED");
        JsonNode done = await(c, id, Duration.ofSeconds(10), false, VoiceIT::ended);
        assertThat(done.path("endReason").asString()).isEqualTo("USER");

        JsonNode s = done.path("settlement");
        BigDecimal cost = providerCost(id);
        assertThat(cost).isPositive();
        // La transcripción del alumno se registra aparte, con el modelo y la tarifa de transcripción (no los del
        // modelo realtime), y entra en el mismo cobro.
        var transcription = jdbc.queryForList("""
                select m.model_code, u.provider_request_id, u.provider_cost, u.pricing_version_id,
                       s.transcription_pricing_version_id
                from ai_usage_records u
                join voice_sessions s on s.id = u.subject_id and u.subject_type = 'VOICE_SESSION'
                join ai_models m on m.id = u.model_id
                where s.public_id = ? and m.modality = 'TRANSCRIPTION'""", id);
        assertThat(transcription).isNotEmpty().allSatisfy(row -> {
            assertThat(row.get("model_code")).isEqualTo("fake-transcribe-1");
            assertThat((String) row.get("provider_request_id")).startsWith("transcription:");
            assertThat(row.get("pricing_version_id")).isEqualTo(row.get("transcription_pricing_version_id"));
            // 2.4 s a 0.30 MXN/min = 0.012 MXN por turno.
            assertThat((BigDecimal) row.get("provider_cost")).isEqualByComparingTo("0.012");
        });
        assertThat(s.path("internal").asBoolean()).isFalse();
        assertThat(s.path("factorApplied").decimalValue()).isEqualByComparingTo("1.30");
        assertThat(s.path("directCostMxn").decimalValue()).isEqualByComparingTo(cost.setScale(2, RoundingMode.HALF_UP));
        assertThat(s.path("totalMxn").decimalValue())
                .isEqualByComparingTo(cost.multiply(new BigDecimal("1.30")).setScale(2, RoundingMode.HALF_UP));

        JsonNode wallet = c.get("/api/v1/me/wallet").json();
        assertThat(wallet.path("reserved").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(wallet.path("available").decimalValue())
                .isEqualByComparingTo(new BigDecimal("100.00").subtract(s.path("totalMxn").decimalValue()));
        assertThat(c.get("/api/v1/voice/sessions").json().get(0).path("id").asString()).isEqualTo(id);

        // Liberada la llamada, puede iniciar otra.
        String next = startCall(c).path("sessionId").asString();
        c.post("/api/v1/voice/sessions/" + next + "/end", null);
        await(c, next, Duration.ofSeconds(10), false, VoiceIT::ended);
        assertLedgerClean();
    }

    @Test
    void adminCallIsRecordedAtCostAsInternalExpense() {
        ApiClient admin = admin();
        JsonNode started = startCall(admin);
        String id = started.path("sessionId").asString();
        assertThat(started.path("reservedMxn").decimalValue()).isEqualByComparingTo("0");
        await(admin, id, Duration.ofSeconds(10), true, v -> v.path("measured").path("totalMxn").decimalValue().signum() > 0);
        admin.post("/api/v1/voice/sessions/" + id + "/end", null);
        JsonNode s = await(admin, id, Duration.ofSeconds(10), false, VoiceIT::ended).path("settlement");
        assertThat(s.path("internal").asBoolean()).isTrue();
        assertThat(s.path("factorApplied").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(s.path("markupMxn").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(s.path("totalMxn").decimalValue())
                .isEqualByComparingTo(providerCost(id).setScale(2, RoundingMode.HALF_UP));
        assertLedgerClean();
    }

    @Test
    void callIsCutWhenTheReservationCannotBeExtended() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00");
        // El límite mensual alcanza para la reserva mínima (2 minutos ≈ 6.15 con la transcripción incluida en el
        // estimado simulado) pero no para ampliarla.
        c.put("/api/v1/me/wallet/preferences", Map.of("monthlyLimit", 7, "lowBalanceThreshold", 20));
        JsonNode started = startCall(c);
        String id = started.path("sessionId").asString();
        assertThat(started.path("maxDurationSeconds").asInt()).isEqualTo(120);
        BigDecimal reserved = started.path("reservedMxn").decimalValue();

        JsonNode done = await(c, id, Duration.ofSeconds(40), true, VoiceIT::ended);
        assertThat(done.path("endReason").asString()).isEqualTo("RESERVE_LIMIT");
        BigDecimal total = done.path("settlement").path("totalMxn").decimalValue();
        assertThat(total).isGreaterThanOrEqualTo(reserved.multiply(new BigDecimal("0.90")).setScale(2, RoundingMode.DOWN));
        // Los eventos que llegan durante el cierre pueden exceder un poco la reserva; nunca más que el sobregiro técnico.
        assertThat(total).isLessThanOrEqualTo(reserved.add(new BigDecimal("10.00")));
        assertThat(c.get("/api/v1/me/wallet").json().path("reserved").decimalValue()).isEqualByComparingTo("0.00");
        assertLedgerClean();
    }

    @Test
    void monthlyLimitBelowTheMinimumBlocksTheCall() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00");
        c.put("/api/v1/me/wallet/preferences", Map.of("monthlyLimit", 3, "lowBalanceThreshold", 20));
        var res = c.post("/api/v1/voice/sessions", Map.of("mode", "FREE"));
        assertThat(res.status()).isEqualTo(402);
        assertThat(res.code()).isEqualTo("MONTHLY_LIMIT_REACHED");
    }

    @Test
    void callEndsWhenTheClientStopsSendingHeartbeats() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00");
        String id = startCall(c).path("sessionId").asString();
        JsonNode done = await(c, id, Duration.ofSeconds(15), false, VoiceIT::ended);
        assertThat(done.path("endReason").asString()).isEqualTo("CLIENT_LOST");
        assertThat(c.get("/api/v1/me/wallet").json().path("reserved").decimalValue()).isEqualByComparingTo("0.00");
    }

    @Test
    void callsAreOwnedAndTranscriptsRespectThePrivacySetting() {
        ApiClient me = verifiedStudent(uniqueEmail());
        topup(me, "100.00");
        String id = startCall(me).path("sessionId").asString();
        ApiClient other = verifiedStudent(uniqueEmail());
        assertThat(other.get("/api/v1/voice/sessions/" + id).status()).isEqualTo(404);
        assertThat(other.post("/api/v1/voice/sessions/" + id + "/end", null).status()).isEqualTo(404);
        assertThat(other.post("/api/v1/voice/sessions/" + id + "/heartbeat", null).status()).isEqualTo(404);
        me.post("/api/v1/voice/sessions/" + id + "/end", null);
        await(me, id, Duration.ofSeconds(10), false, VoiceIT::ended);

        // Con la preferencia desactivada no se guarda transcripción (el audio nunca se guarda).
        assertThat(me.patch("/api/v1/me/profile", Map.of("storeTranscripts", false)).status()).isEqualTo(200);
        String id2 = startCall(me).path("sessionId").asString();
        await(me, id2, Duration.ofSeconds(10), true,
                v -> v.path("measured").path("totalMxn").decimalValue().signum() > 0);
        me.post("/api/v1/voice/sessions/" + id2 + "/end", null);
        JsonNode done = await(me, id2, Duration.ofSeconds(10), false, VoiceIT::ended);
        assertThat(done.path("transcriptStored").asBoolean()).isFalse();
        assertThat(done.path("transcript").size()).isZero();
    }
}
