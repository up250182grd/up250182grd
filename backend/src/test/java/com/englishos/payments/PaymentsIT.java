package com.englishos.payments;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import com.englishos.payments.domain.WebhookSignature;
import com.englishos.support.ApiClient;
import com.englishos.support.FakeMercadoPago;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Fase 7 de punta a punta: el adaptador HTTP real de Mercado Pago contra un servidor simulado local, MySQL real y el
 * webhook público. Cubre los criterios de aceptación: firma inválida, evento repetido, monto manipulado y regreso sin
 * webhook → sin crédito; además conciliación, devoluciones, reportes CSV que cuadran con el libro y auditoría.
 */
class PaymentsIT extends IntegrationTest {

    private static final String SECRET = "secreto-webhook-de-prueba";
    private static final FakeMercadoPago MP;

    static {
        try {
            MP = new FakeMercadoPago();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void mercadoPago(DynamicPropertyRegistry r) {
        r.add("app.payments.provider", () -> "mercadopago");
        r.add("app.payments.mercadopago.access-token", () -> "TEST-token-de-prueba");
        r.add("app.payments.mercadopago.webhook-secret", () -> SECRET);
        r.add("app.payments.mercadopago.base-url", MP::baseUrl);
        r.add("app.payments.notification-url", () -> "https://englishos.example/api/v1/webhooks/mercadopago");
    }

    @AfterAll
    static void stop() {
        MP.close();
    }

    @Autowired
    JdbcTemplate jdbc;

    private final HttpClient http = HttpClient.newHttpClient();

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private JsonNode createTopup(ApiClient c, String amount) {
        var res = c.post("/api/v1/me/wallet/topups", Map.of("amount", new BigDecimal(amount), "idempotencyKey", key()));
        assertThat(res.status()).as(res.body()).isEqualTo(201);
        return res.json();
    }

    private BigDecimal available(ApiClient c) {
        return c.get("/api/v1/me/wallet").json().path("available").decimalValue();
    }

    /** Envía una notificación como lo haría la pasarela (sin sesión ni CSRF). */
    private int webhook(String paymentId, String secret, String requestId) {
        String ts = String.valueOf(System.currentTimeMillis());
        String v1 = WebhookSignature.hmacHex(secret, WebhookSignature.manifest(paymentId, requestId, ts));
        String body = "{\"action\":\"payment.updated\",\"type\":\"payment\",\"data\":{\"id\":\"" + paymentId
                + "\"},\"transaction_amount\":999999}";
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/webhooks/mercadopago?data.id=" + paymentId + "&type=payment"))
                .header("Content-Type", "application/json")
                .header("x-signature", "ts=" + ts + ",v1=" + v1)
                .header("x-request-id", requestId)
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        try {
            return http.send(req, HttpResponse.BodyHandlers.discarding()).statusCode();
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
    }

    private int webhook(String paymentId) {
        return webhook(paymentId, SECRET, key());
    }

    private int ledgerTopups(String paymentId) {
        return jdbc.queryForObject("select count(*) from ledger_transactions where idempotency_key = ?", Integer.class,
                "topup:gw:MERCADOPAGO:" + paymentId);
    }

    private List<String> ledgerProblems() {
        return admin().get("/api/v1/admin/billing/summary").json().path("ledgerProblems").valueStream()
                .map(JsonNode::asString).toList();
    }

    // ---- Recarga y notificación ---------------------------------------------------------------

    @Test
    void topupCreatesACheckoutAndOnlyAVerifiedWebhookCreditsIt() {
        ApiClient c = verifiedStudent(uniqueEmail());
        assertThat(c.get("/api/v1/payments/config").json().path("enabled").asBoolean()).isTrue();

        String k = key();
        JsonNode t = c.post("/api/v1/me/wallet/topups", Map.of("amount", new BigDecimal("200.00"), "idempotencyKey", k))
                .json();
        String id = t.path("id").asString();
        assertThat(t.path("checkoutUrl").asString()).startsWith("https://sandbox.mercadopago.com.mx/");
        assertThat(t.path("status").asString()).isEqualTo("CREATED");
        // Reintento del mismo clic: misma recarga, sin otra preferencia.
        assertThat(c.post("/api/v1/me/wallet/topups", Map.of("amount", new BigDecimal("200.00"), "idempotencyKey", k))
                .json().path("id").asString()).isEqualTo(id);

        // Lo que se envió a la pasarela: monto del servidor, referencia y credenciales en cabecera.
        var pref = MP.requests.stream().filter(r -> r.path().equals("/checkout/preferences")
                && r.body().contains(id)).toList();
        assertThat(pref).hasSize(1);
        assertThat(pref.get(0).body()).contains("\"unit_price\":200.00", "\"currency_id\":\"MXN\"",
                "\"external_reference\":\"" + id + "\"", "/app/wallet/topups/" + id);

        // Regreso desde la pasarela sin notificación: NO hay crédito.
        assertThat(c.get("/api/v1/me/wallet/topups/" + id).json().path("credited").asBoolean()).isFalse();
        assertThat(available(c)).isEqualByComparingTo("0.00");

        String paymentId = MP.addPayment("approved", "200.00", "MXN", id);
        assertThat(webhook(paymentId)).isEqualTo(200);
        JsonNode after = c.get("/api/v1/me/wallet/topups/" + id).json();
        assertThat(after.path("status").asString()).isEqualTo("APPROVED");
        assertThat(after.path("credited").asBoolean()).isTrue();
        assertThat(available(c)).isEqualByComparingTo("200.00");

        // Evento repetido: no vuelve a acreditar.
        assertThat(webhook(paymentId)).isEqualTo(200);
        assertThat(webhook(paymentId)).isEqualTo(200);
        assertThat(available(c)).isEqualByComparingTo("200.00");
        assertThat(ledgerTopups(paymentId)).isEqualTo(1);
        assertThat(ledgerProblems()).isEmpty();
    }

    @Test
    void parallelNotificationsCreditOnce() throws Exception {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "300.00").path("id").asString();
        String paymentId = MP.addPayment("approved", "300.00", "MXN", id);
        ExecutorService pool = Executors.newFixedThreadPool(6);
        CountDownLatch go = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            results.add(pool.submit(() -> {
                go.await();
                return webhook(paymentId);
            }));
        }
        go.countDown();
        for (Future<Integer> f : results) {
            assertThat(f.get()).isEqualTo(200);
        }
        pool.shutdown();
        assertThat(available(c)).isEqualByComparingTo("300.00");
        assertThat(ledgerTopups(paymentId)).isEqualTo(1);
    }

    @Test
    void invalidSignatureIsRejectedWithoutQueryingOrCrediting() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "150.00").path("id").asString();
        String paymentId = MP.addPayment("approved", "150.00", "MXN", id);
        long fetchesBefore = MP.countRequests("GET", "/v1/payments/" + paymentId);

        assertThat(webhook(paymentId, "clave-equivocada", key())).isEqualTo(401);
        assertThat(MP.countRequests("GET", "/v1/payments/" + paymentId)).isEqualTo(fetchesBefore);
        assertThat(available(c)).isEqualByComparingTo("0.00");
        assertThat(jdbc.queryForObject("""
                select count(*) from payment_webhook_events
                where data_id = ? and signature_valid = false and outcome = 'INVALID_SIGNATURE'""",
                Integer.class, paymentId)).isEqualTo(1);

        // Sin cabecera de firma.
        HttpRequest unsigned = HttpRequest.newBuilder(URI.create("http://localhost:" + port
                        + "/api/v1/webhooks/mercadopago?data.id=" + paymentId + "&type=payment"))
                .POST(HttpRequest.BodyPublishers.ofString("{}")).build();
        try {
            assertThat(http.send(unsigned, HttpResponse.BodyHandlers.discarding()).statusCode()).isEqualTo(401);
        } catch (IOException | InterruptedException e) {
            throw new IllegalStateException(e);
        }
        assertThat(available(c)).isEqualByComparingTo("0.00");
    }

    @Test
    void manipulatedAmountOrCurrencyIsNeverCredited() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "200.00").path("id").asString();
        // El pago real es de 100 aunque la recarga sea de 200 (y la notificación diga 999999 en su cuerpo).
        String cheaper = MP.addPayment("approved", "100.00", "MXN", id);
        assertThat(webhook(cheaper)).isEqualTo(200);
        assertThat(available(c)).isEqualByComparingTo("0.00");
        assertThat(c.get("/api/v1/me/wallet/topups/" + id).json().path("status").asString()).isEqualTo("REVIEW");
        assertThat(jdbc.queryForObject("select count(*) from billing_incidents where type = 'AMOUNT_MISMATCH' and ref_id = ?",
                Integer.class, id)).isEqualTo(1);

        String id2 = createTopup(c, "200.00").path("id").asString();
        assertThat(webhook(MP.addPayment("approved", "200.00", "USD", id2))).isEqualTo(200);
        assertThat(available(c)).isEqualByComparingTo("0.00");

        // Pago de una referencia desconocida: incidencia, sin crédito.
        assertThat(webhook(MP.addPayment("approved", "200.00", "MXN", "NO-EXISTE"))).isEqualTo(200);
    }

    @Test
    void pendingAndRejectedPaymentsDoNotCredit() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "120.00").path("id").asString();
        webhook(MP.addPayment("in_process", "120.00", "MXN", id));
        assertThat(c.get("/api/v1/me/wallet/topups/" + id).json().path("status").asString()).isEqualTo("PENDING");
        String id2 = createTopup(c, "120.00").path("id").asString();
        webhook(MP.addPayment("rejected", "120.00", "MXN", id2));
        assertThat(c.get("/api/v1/me/wallet/topups/" + id2).json().path("status").asString()).isEqualTo("REJECTED");
        assertThat(available(c)).isEqualByComparingTo("0.00");
    }

    @Test
    void topupsAreValidatedAndOwned() {
        ApiClient unverified = newClient();
        String email = uniqueEmail();
        register(unverified, email, STRONG_PASSWORD);
        unverified.login(email, STRONG_PASSWORD);
        assertThat(unverified.post("/api/v1/me/wallet/topups", Map.of("amount", 200, "idempotencyKey", key())).code())
                .isEqualTo("EMAIL_NOT_VERIFIED");

        ApiClient c = verifiedStudent(uniqueEmail());
        assertThat(c.post("/api/v1/me/wallet/topups", Map.of("amount", 50, "idempotencyKey", key())).code())
                .isEqualTo("INVALID_TOPUP_AMOUNT");
        assertThat(c.post("/api/v1/me/wallet/topups", Map.of("amount", 100.001, "idempotencyKey", key())).status())
                .isEqualTo(400);
        String id = createTopup(c, "100.00").path("id").asString();
        ApiClient other = verifiedStudent(uniqueEmail());
        assertThat(other.get("/api/v1/me/wallet/topups/" + id).status()).isEqualTo(404);
        assertThat(other.get("/api/v1/admin/payments").status()).isEqualTo(403);
        assertThat(other.post("/api/v1/admin/payments/" + id + "/refunds", Map.of("amount", 10, "note", "no soy admin"))
                .status()).isEqualTo(403);
    }

    // ---- Administración: conciliación, devoluciones, incidencias ------------------------------

    @Test
    void adminReconcilesALostNotification() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "250.00").path("id").asString();
        MP.addPayment("approved", "250.00", "MXN", id); // la notificación nunca llegó
        ApiClient admin = admin();
        JsonNode r = admin.post("/api/v1/admin/payments/" + id + "/reconcile", null).json();
        assertThat(r.path("credited").asBoolean()).isTrue();
        assertThat(available(c)).isEqualByComparingTo("250.00");
        // Conciliar otra vez no duplica.
        admin.post("/api/v1/admin/payments/" + id + "/reconcile", null);
        assertThat(available(c)).isEqualByComparingTo("250.00");
    }

    @Test
    void manualRefundsMoveOnlyUnusedBalanceAndRollBackIfTheGatewayRejects() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "200.00").path("id").asString();
        webhook(MP.addPayment("approved", "200.00", "MXN", id));
        ApiClient admin = admin();

        assertThat(admin.post("/api/v1/admin/payments/" + id + "/refunds", Map.of("amount", 50, "note", "x")).code())
                .isEqualTo("NOTE_REQUIRED");
        assertThat(admin.post("/api/v1/admin/payments/" + id + "/refunds",
                Map.of("amount", 250, "note", "Más que la recarga")).code()).isEqualTo("REFUND_EXCEEDS_TOPUP");

        JsonNode r = admin.post("/api/v1/admin/payments/" + id + "/refunds",
                Map.of("amount", 50, "note", "Solicitud del alumno por correo")).json();
        assertThat(r.path("status").asString()).isEqualTo("PARTIALLY_REFUNDED");
        assertThat(r.path("refundableMxn").decimalValue()).isEqualByComparingTo("150.00");
        assertThat(r.path("refunds").get(0).path("status").asString()).isEqualTo("COMPLETED");
        assertThat(available(c)).isEqualByComparingTo("150.00");

        // La pasarela rechaza: el saldo se repone y queda incidencia.
        MP.failRefunds.set(true);
        try {
            var failed = admin.post("/api/v1/admin/payments/" + id + "/refunds",
                    Map.of("amount", 30, "note", "Prueba de rechazo de la pasarela"));
            assertThat(failed.status()).isEqualTo(502);
            assertThat(failed.code()).isEqualTo("REFUND_FAILED");
        } finally {
            MP.failRefunds.set(false);
        }
        assertThat(available(c)).isEqualByComparingTo("150.00");
        JsonNode incidents = admin.get("/api/v1/admin/billing/summary").json().path("openIncidents");
        Long incidentId = incidents.valueStream().filter(i -> "REFUND_FAILED".equals(i.path("type").asString())
                && id.equals(i.path("refId").asString())).findFirst().orElseThrow().path("id").asLong();

        // Si el alumno ya gastó el saldo, no se puede devolver más de lo disponible.
        c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")); // reserva 5.00
        var tooMuch = admin.post("/api/v1/admin/payments/" + id + "/refunds",
                Map.of("amount", 150, "note", "Devolver todo lo que queda"));
        assertThat(tooMuch.code()).isEqualTo("INSUFFICIENT_BALANCE_FOR_REFUND");
        assertThat(available(c)).isEqualByComparingTo("145.00");

        // Resolver la incidencia queda auditado.
        assertThat(admin.post("/api/v1/admin/billing/incidents/" + incidentId + "/resolve",
                Map.of("note", "Revisado con la pasarela")).status()).isEqualTo(204);
        assertThat(admin.post("/api/v1/admin/billing/incidents/" + incidentId + "/resolve",
                Map.of("note", "Otra vez")).status()).isEqualTo(404);
        JsonNode audit = admin.get("/api/v1/admin/audit?targetType=PAYMENT_INTENT&targetId=" + id).json();
        assertThat(audit.path("items").valueStream().map(a -> a.path("action").asString()))
                .contains("PAYMENT_INTENT_CREATED", "PAYMENT_REFUND_REQUESTED", "PAYMENT_REFUND_COMPLETED");
        assertThat(admin.get("/api/v1/admin/audit?action=BILLING_INCIDENT_RESOLVED").json().path("items").size())
                .isPositive();
        assertThat(ledgerProblems()).isEmpty();
    }

    // ---- Reportes -----------------------------------------------------------------------------

    @Test
    void csvReportsBalanceWithTheLedgerAndTheSummary() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String id = createTopup(c, "180.00").path("id").asString();
        webhook(MP.addPayment("approved", "180.00", "MXN", id));
        ApiClient admin = admin();

        var ledger = admin.get("/api/v1/admin/reports/ledger.csv");
        assertThat(ledger.status()).isEqualTo(200);
        assertThat(ledger.body()).startsWith("﻿fecha_utc,asiento,");
        // Cada asiento del reporte suma cero (partida doble) y el total del reporte es cero.
        Map<String, BigDecimal> byTx = new java.util.HashMap<>();
        ledger.body().lines().skip(1).forEach(line -> {
            String[] cols = parseCsv(line);
            byTx.merge(cols[1], new BigDecimal(cols[cols.length - 1]), BigDecimal::add);
        });
        assertThat(byTx).isNotEmpty();
        assertThat(byTx.values()).allSatisfy(sum -> assertThat(sum).isEqualByComparingTo("0"));

        String payments = admin.get("/api/v1/admin/reports/payments.csv").body();
        assertThat(payments).contains(id).contains("APPROVED");

        // Liquidaciones del mes: los totales del CSV coinciden con el resumen financiero.
        String settlements = admin.get("/api/v1/admin/reports/settlements.csv").body();
        BigDecimal csvTotal = settlements.lines().skip(1).map(WalletCsv::cols)
                .filter(cols -> !"PENDING_RECONCILIATION".equals(cols[6]))
                .map(cols -> new BigDecimal(cols[15])).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal summaryTotal = admin.get("/api/v1/admin/billing/summary").json().path("totalsByRole").valueStream()
                .map(r -> r.path("totalMxn").decimalValue()).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(csvTotal).isEqualByComparingTo(summaryTotal);

        assertThat(c.get("/api/v1/admin/reports/ledger.csv").status()).isEqualTo(403);
        assertThat(admin.get("/api/v1/admin/reports/ledger.csv?month=2026-13").code()).isEqualTo("INVALID_MONTH");
    }

    private static String[] parseCsv(String line) {
        return WalletCsv.cols(line);
    }

    /** Lector CSV mínimo (comillas dobles) solo para la prueba. */
    static final class WalletCsv {
        static String[] cols(String line) {
            List<String> out = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            boolean quoted = false;
            for (int i = 0; i < line.length(); i++) {
                char ch = line.charAt(i);
                if (quoted) {
                    if (ch == '"' && i + 1 < line.length() && line.charAt(i + 1) == '"') {
                        cur.append('"');
                        i++;
                    } else if (ch == '"') {
                        quoted = false;
                    } else {
                        cur.append(ch);
                    }
                } else if (ch == '"') {
                    quoted = true;
                } else if (ch == ',') {
                    out.add(cur.toString());
                    cur.setLength(0);
                } else {
                    cur.append(ch);
                }
            }
            out.add(cur.toString());
            return out.toArray(String[]::new);
        }
    }
}
