package com.englishos.billing;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 4 de punta a punta contra MySQL real: billetera de doble partida, reservas, tutor con streaming, medición
 * informada por el proveedor (simulado) y liquidación con factor 1.30 (alumno) y 1.00 (administrador).
 */
class WalletAndTutorIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private ApiClient.Response topup(ApiClient c, String amount, String key) {
        return c.post("/api/v1/me/wallet/simulated-topups", Map.of("amount", new BigDecimal(amount), "idempotencyKey", key));
    }

    private BigDecimal available(ApiClient c) {
        return c.get("/api/v1/me/wallet").json().path("available").decimalValue();
    }

    private String startConversation(ApiClient c) {
        var res = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE", "topic", "my weekend"));
        assertThat(res.status()).as(res.body()).isEqualTo(201);
        return res.json().path("id").asString();
    }

    private JsonNode say(ApiClient c, String conversationId, String text) {
        List<Map.Entry<String, JsonNode>> events =
                c.postSse("/api/v1/tutor/conversations/" + conversationId + "/messages", Map.of("text", text));
        String streamed = events.stream().filter(e -> e.getKey().equals("delta"))
                .map(e -> e.getValue().path("text").asString()).reduce("", String::concat);
        JsonNode done = events.stream().filter(e -> e.getKey().equals("done")).map(Map.Entry::getValue)
                .findFirst().orElseThrow(() -> new AssertionError("Sin evento done: " + events));
        assertThat(streamed).as("el marcador y el JSON de correcciones nunca llegan al alumno")
                .doesNotContain("<<<CORRECTIONS>>>").doesNotContain("explanation_es");
        assertThat(streamed.trim()).isEqualTo(done.path("reply").asString());
        return done;
    }

    private List<String> ledgerProblems() {
        return admin().get("/api/v1/admin/billing/summary").json().path("ledgerProblems").valueStream()
                .map(JsonNode::asString).toList();
    }

    /** Recalcula el cargo esperado desde el uso registrado (§5.2) para compararlo con la liquidación. */
    private BigDecimal[] expectedCharge(String conversationPublicId, String factor) {
        BigDecimal cost = jdbc.queryForObject("""
                select coalesce(sum(u.provider_cost), 0) from ai_usage_records u
                join ai_conversations c on c.id = u.subject_id and u.subject_type = 'TEXT_CONVERSATION'
                where c.public_id = ?""", BigDecimal.class, conversationPublicId);
        BigDecimal direct = cost.setScale(2, RoundingMode.HALF_UP);
        BigDecimal price = cost.multiply(new BigDecimal(factor)).setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal[] {cost, direct, price};
    }

    // ---- Billetera ----------------------------------------------------------------------------------

    @Test
    void simulatedTopupIsIdempotentAndValidated() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String k = key();
        assertThat(topup(c, "150.00", k).status()).isEqualTo(200);
        assertThat(topup(c, "150.00", k).status()).isEqualTo(200); // reintento: no duplica
        assertThat(available(c)).isEqualByComparingTo("150.00");

        assertThat(topup(c, "50.00", key()).code()).isEqualTo("INVALID_TOPUP_AMOUNT");
        assertThat(topup(c, "100.001", key()).status()).isEqualTo(400);

        var movements = c.get("/api/v1/me/wallet/movements").json();
        assertThat(movements.size()).isEqualTo(1);
        assertThat(movements.get(0).path("type").asString()).isEqualTo("TOPUP");
        assertThat(movements.get(0).path("availableChange").decimalValue()).isEqualByComparingTo("150.00");
    }

    @Test
    void parallelTopupsWithTheSameKeyCreditOnce() throws Exception {
        ApiClient c = verifiedStudent(uniqueEmail());
        String k = key();
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return topup(c, "100.00", k).status();
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isIn(200, 409);
        }
        pool.shutdown();
        assertThat(available(c)).isEqualByComparingTo("100.00");
    }

    // ---- Tutor y cobro ------------------------------------------------------------------------------

    @Test
    void tutorRequiresVerifiedEmailAndEnoughBalance() {
        ApiClient unverified = newClient();
        String email = uniqueEmail();
        register(unverified, email, STRONG_PASSWORD);
        unverified.login(email, STRONG_PASSWORD);
        assertThat(unverified.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).code())
                .isEqualTo("EMAIL_NOT_VERIFIED");

        ApiClient noMoney = verifiedStudent(uniqueEmail());
        var res = noMoney.post("/api/v1/tutor/conversations", Map.of("mode", "FREE"));
        assertThat(res.status()).isEqualTo(402);
        assertThat(res.code()).isEqualTo("INSUFFICIENT_FUNDS");
        assertThat(available(noMoney)).isEqualByComparingTo("0.00"); // nunca queda negativo al reservar
    }

    @Test
    void studentConversationIsMeteredByTheServerAndChargedAtCostPlus30Percent() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00", key());
        String conv = startConversation(c);

        JsonNode wallet = c.get("/api/v1/me/wallet").json();
        assertThat(wallet.path("reserved").decimalValue()).isEqualByComparingTo("5.00");
        assertThat(wallet.path("available").decimalValue()).isEqualByComparingTo("95.00");

        JsonNode first = say(c, conv, "Hello! She have a cat and I is happy.");
        assertThat(first.path("corrections").size()).isEqualTo(2);
        assertThat(first.path("corrections").toString()).contains("She has", "I am");
        assertThat(first.path("measured").path("totalMxn").decimalValue()).isPositive();
        say(c, conv, "Yesterday I went to the park with my family.");

        // El uso viene del proveedor (simulado) y se registra en el servidor, una vez por respuesta.
        Integer usageRows = jdbc.queryForObject("""
                select count(*) from ai_usage_records u join ai_conversations c on c.id = u.subject_id
                where c.public_id = ? and u.data_source = 'SIMULATED'""", Integer.class, conv);
        assertThat(usageRows).isEqualTo(2);

        JsonNode closed = c.post("/api/v1/tutor/conversations/" + conv + "/close", null).json();
        assertThat(closed.path("status").asString()).isEqualTo("CLOSED");
        JsonNode s = closed.path("settlement");
        BigDecimal[] expected = expectedCharge(conv, "1.30");
        assertThat(s.path("status").asString()).isEqualTo("SETTLED");
        assertThat(s.path("factorApplied").decimalValue()).isEqualByComparingTo("1.30");
        assertThat(s.path("directCostMxn").decimalValue()).isEqualByComparingTo(expected[1]);
        assertThat(s.path("totalMxn").decimalValue()).isEqualByComparingTo(expected[2]);
        assertThat(s.path("markupMxn").decimalValue())
                .isEqualByComparingTo(expected[2].subtract(expected[1]));
        assertThat(s.path("internal").asBoolean()).isFalse();
        assertThat(closed.path("correctionsByCategory").path("GRAMMAR").asInt()).isEqualTo(2);

        // Se cobró lo consumido y se liberó el resto de la reserva.
        JsonNode after = c.get("/api/v1/me/wallet").json();
        assertThat(after.path("reserved").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(after.path("available").decimalValue())
                .isEqualByComparingTo(new BigDecimal("100.00").subtract(expected[2]));
        assertThat(after.path("spentThisMonth").decimalValue()).isEqualByComparingTo(expected[2]);

        // Cerrar otra vez no cobra dos veces.
        c.post("/api/v1/tutor/conversations/" + conv + "/close", null);
        assertThat(available(c)).isEqualByComparingTo(new BigDecimal("100.00").subtract(expected[2]));
        assertThat(c.get("/api/v1/me/usage").json().size()).isEqualTo(1);
        assertThat(ledgerProblems()).isEmpty();
    }

    @Test
    void adminConversationIsRecordedAtCostAsInternalExpense() {
        ApiClient admin = admin();
        BigDecimal adminBalanceBefore = available(admin);
        BigDecimal internalBefore = admin.get("/api/v1/admin/billing/summary").json()
                .path("businessAccounts").path("INTERNAL_AI_EXPENSE").decimalValue();

        String conv = startConversation(admin);
        assertThat(admin.get("/api/v1/tutor/conversations/" + conv).json().path("reservedMxn").decimalValue())
                .isEqualByComparingTo("0"); // el administrador no reserva ni paga por pasarela
        say(admin, conv, "Let's practice the present perfect, please.");
        JsonNode s = admin.post("/api/v1/tutor/conversations/" + conv + "/close", null).json().path("settlement");

        BigDecimal[] expected = expectedCharge(conv, "1.00");
        assertThat(s.path("factorApplied").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(s.path("markupMxn").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(s.path("totalMxn").decimalValue()).isEqualByComparingTo(expected[1]);
        assertThat(s.path("internal").asBoolean()).isTrue();
        assertThat(available(admin)).isEqualByComparingTo(adminBalanceBefore);
        BigDecimal internalAfter = admin.get("/api/v1/admin/billing/summary").json()
                .path("businessAccounts").path("INTERNAL_AI_EXPENSE").decimalValue();
        assertThat(internalAfter.subtract(internalBefore)).isEqualByComparingTo(expected[1]);
        assertThat(admin.get("/api/v1/admin/billing/my-usage").json().toString()).contains(s.path("id").asString());
        assertThat(ledgerProblems()).isEmpty();
    }

    @Test
    void factorChangesAreNeverRetroactive() {
        ApiClient admin = admin();
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00", key());
        String before = startConversation(c); // fija la política 1.30

        var created = admin.post("/api/v1/admin/billing/policies", Map.of("roleCode", "STUDENT", "factor", 1.5,
                "taxMode", "NONE", "note", "Prueba de integración: el factor nuevo no es retroactivo"));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        try {
            say(c, before, "I like music.");
            JsonNode s1 = c.post("/api/v1/tutor/conversations/" + before + "/close", null).json().path("settlement");
            assertThat(s1.path("factorApplied").decimalValue()).isEqualByComparingTo("1.30");

            String after = startConversation(c);
            say(c, after, "I like books.");
            JsonNode s2 = c.post("/api/v1/tutor/conversations/" + after + "/close", null).json().path("settlement");
            assertThat(s2.path("factorApplied").decimalValue()).isEqualByComparingTo("1.50");
        } finally {
            admin.post("/api/v1/admin/billing/policies", Map.of("roleCode", "STUDENT", "factor", 1.3,
                    "taxMode", "NONE", "note", "Restaurar el valor inicial obligatorio (1.30)"));
        }
        assertThat(admin.get("/api/v1/admin/billing/policies").body()).contains("Prueba de integración");
    }

    @Test
    void monthlyLimitIsEnforcedBeforeSpending() {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00", key());
        assertThat(c.put("/api/v1/me/wallet/preferences", Map.of("monthlyLimit", 3, "lowBalanceThreshold", 20))
                .json().path("monthlyLimit").decimalValue()).isEqualByComparingTo("3.00");
        var res = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE"));
        assertThat(res.status()).isEqualTo(402);
        assertThat(res.code()).isEqualTo("MONTHLY_LIMIT_REACHED");
    }

    @Test
    void parallelConversationsCannotReserveMoreThanTheBalance() throws Exception {
        ApiClient c = verifiedStudent(uniqueEmail());
        topup(c, "100.00", key()); // alcanza exactamente para 20 reservas iniciales de 5.00

        ExecutorService pool = Executors.newFixedThreadPool(25);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < 25; i++) {
            results.add(pool.submit(() -> {
                go.await();
                var r = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE"));
                return r.status() == 201 ? "201" : r.status() + ":" + r.code();
            }));
        }
        go.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();
        assertThat(outcomes).as(outcomes.toString()).filteredOn("201"::equals).hasSize(20);
        assertThat(outcomes).as(outcomes.toString()).filteredOn("402:INSUFFICIENT_FUNDS"::equals).hasSize(5);
        JsonNode wallet = c.get("/api/v1/me/wallet").json();
        assertThat(wallet.path("available").decimalValue()).isEqualByComparingTo("0.00"); // nunca negativo
        assertThat(wallet.path("reserved").decimalValue()).isEqualByComparingTo("100.00");
        assertThat(ledgerProblems()).isEmpty();
    }

    @Test
    void conversationsAreOwnedAndValidated() {
        ApiClient me = verifiedStudent(uniqueEmail());
        topup(me, "100.00", key());
        String conv = startConversation(me);
        ApiClient other = verifiedStudent(uniqueEmail());
        assertThat(other.get("/api/v1/tutor/conversations/" + conv).status()).isEqualTo(404);
        assertThat(other.post("/api/v1/tutor/conversations/" + conv + "/close", null).status()).isEqualTo(404);
        assertThat(other.post("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "hi")).status())
                .isEqualTo(404);

        assertThat(me.post("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "   ")).status())
                .isEqualTo(400);
        me.post("/api/v1/tutor/conversations/" + conv + "/close", null);
        assertThat(me.post("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "hello")).code())
                .isEqualTo("CONVERSATION_CLOSED");
    }

    // ---- Administración de precios ------------------------------------------------------------------

    @Test
    void pricingAdministrationIsProtectedValidatedAndAudited() {
        ApiClient student = verifiedStudent(uniqueEmail());
        assertThat(student.get("/api/v1/admin/billing/models").status()).isEqualTo(403);
        assertThat(student.post("/api/v1/admin/billing/policies", Map.of("roleCode", "STUDENT", "factor", 1.0,
                "taxMode", "NONE", "note", "x")).status()).isEqualTo(403);

        ApiClient admin = admin();
        JsonNode models = admin.get("/api/v1/admin/billing/models").json();
        assertThat(models.toString()).contains("fake-tutor-1", "\"simulated\":true");

        String code = "it-model-" + UUID.randomUUID().toString().substring(0, 8);
        var model = admin.post("/api/v1/admin/billing/models", Map.of("provider", "OPENAI", "modelCode", code,
                "modality", "TEXT", "displayName", "Modelo de prueba"));
        assertThat(model.status()).as(model.body()).isEqualTo(201);
        long modelId = model.json().path("id").asLong();

        var noSource = admin.post("/api/v1/admin/billing/models/" + modelId + "/pricing", Map.of("currency", "USD",
                "ratesPerMillion", Map.of("text_in", 1, "text_out", 4), "sourceUrl", "trust me",
                "verifiedAt", "2026-11-01"));
        assertThat(noSource.code()).isEqualTo("SOURCE_REQUIRED");
        var badUnit = admin.post("/api/v1/admin/billing/models/" + modelId + "/pricing", Map.of("currency", "USD",
                "ratesPerMillion", Map.of("tokens", 1), "sourceUrl", "https://example.com/pricing",
                "verifiedAt", "2026-11-01"));
        assertThat(badUnit.code()).isEqualTo("INVALID_RATES");
        var ok = admin.post("/api/v1/admin/billing/models/" + modelId + "/pricing", Map.of("currency", "USD",
                "ratesPerMillion", Map.of("text_in", 1.25, "text_in_cached", 0.125, "text_out", 10),
                "sourceUrl", "https://example.com/pricing", "verifiedAt", "2026-11-01"));
        assertThat(ok.status()).as(ok.body()).isEqualTo(201);

        assertThat(admin.post("/api/v1/admin/billing/policies", Map.of("roleCode", "STUDENT", "factor", 0.9,
                "taxMode", "NONE", "note", "no")).code()).isEqualTo("INVALID_FACTOR");
        assertThat(admin.post("/api/v1/admin/billing/fx-rates", Map.of("base", "USD", "rate", 18.25,
                "date", "2099-01-01")).code()).isEqualTo("INVALID_FX");

        Integer audited = jdbc.queryForObject(
                "select count(*) from audit_logs where action = 'PRICING_VERSION_CREATED' and target_id = ?",
                Integer.class, String.valueOf(modelId));
        assertThat(audited).isEqualTo(1);

        JsonNode policies = admin.get("/api/v1/admin/billing/policies").json();
        assertThat(policies.valueStream().filter(p -> p.path("current").asBoolean()).map(p ->
                p.path("roleCode").asString() + "=" + p.path("factor").decimalValue().stripTrailingZeros().toPlainString())
                .toList()).contains("ADMIN=1");
    }
}
