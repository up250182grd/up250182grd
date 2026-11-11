package com.englishos.payments.infrastructure;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.englishos.payments.domain.PaymentGateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Adaptador de Mercado Pago (Checkout Pro). Solo se activa con {@code app.payments.provider=mercadopago}.
 *
 * <p>🔍 Contrato tomado de la documentación pública de Mercado Pago tal como se conoce; NO se pudo verificar contra la
 * documentación vigente ni contra la API desde el entorno de desarrollo (dominios bloqueados). Probado contra un
 * servidor simulado que reproduce estas formas. Verificar antes de usar credenciales de prueba reales:
 * <ul>
 * <li>{@code POST /checkout/preferences} con {@code items}, {@code external_reference}, {@code back_urls},
 * {@code notification_url}; respuesta {@code id}, {@code init_point}, {@code sandbox_init_point}.</li>
 * <li>{@code GET /v1/payments/{id}}: {@code status}, {@code status_detail}, {@code transaction_amount},
 * {@code currency_id}, {@code external_reference}.</li>
 * <li>{@code GET /v1/payments/search?external_reference=…}: {@code results[]}.</li>
 * <li>{@code POST /v1/payments/{id}/refunds} con {@code amount} y cabecera {@code X-Idempotency-Key}.</li>
 * </ul>
 */
@Component
@ConditionalOnProperty(name = "app.payments.provider", havingValue = "mercadopago")
class MercadoPagoGateway implements PaymentGateway {

    private static final Logger log = LoggerFactory.getLogger(MercadoPagoGateway.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final String accessToken;
    private final String baseUrl;
    private final boolean testMode;

    MercadoPagoGateway(JsonMapper json, @Value("${app.payments.mercadopago.access-token:}") String accessToken,
            @Value("${app.payments.mercadopago.base-url:https://api.mercadopago.com}") String baseUrl,
            @Value("${app.payments.mercadopago.test-mode:true}") boolean testMode) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalStateException("PAYMENTS_PROVIDER=mercadopago requiere MP_ACCESS_TOKEN");
        }
        this.json = json;
        this.accessToken = accessToken;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.testMode = testMode;
    }

    @Override
    public String provider() {
        return "MERCADOPAGO";
    }

    @Override
    public boolean testMode() {
        return testMode;
    }

    @Override
    public Checkout createCheckout(CheckoutRequest r) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("id", "wallet-topup");
        item.put("title", r.title());
        item.put("quantity", 1);
        item.put("currency_id", r.currency());
        item.put("unit_price", r.amount());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("items", List.of(item));
        body.put("external_reference", r.intentId());
        body.put("back_urls", Map.of("success", r.successUrl(), "pending", r.pendingUrl(), "failure", r.failureUrl()));
        body.put("auto_return", "approved");
        if (r.notificationUrl() != null && !r.notificationUrl().isBlank()) {
            body.put("notification_url", r.notificationUrl());
        }
        if (r.payerEmail() != null) {
            body.put("payer", Map.of("email", r.payerEmail()));
        }
        JsonNode res = send("POST", "/checkout/preferences", json.writeValueAsString(body), "pref-" + r.intentId());
        String url = testMode && res.hasNonNull("sandbox_init_point") ? res.path("sandbox_init_point").asString()
                : res.path("init_point").asString("");
        if (url.isBlank() || !url.startsWith("https://")) {
            throw new GatewayException("La pasarela no devolvió una URL de pago válida");
        }
        return new Checkout(res.path("id").asString(), url);
    }

    @Override
    public Optional<ProviderPayment> fetchPayment(String paymentId) {
        if (paymentId == null || !paymentId.matches("[A-Za-z0-9-]{1,64}")) {
            return Optional.empty();
        }
        try {
            return Optional.of(toPayment(send("GET", "/v1/payments/" + paymentId, null, null)));
        } catch (NotFound e) {
            return Optional.empty();
        }
    }

    @Override
    public List<ProviderPayment> searchByExternalReference(String externalReference) {
        JsonNode res = send("GET", "/v1/payments/search?external_reference="
                + URLEncoder.encode(externalReference, StandardCharsets.UTF_8), null, null);
        List<ProviderPayment> out = new ArrayList<>();
        res.path("results").forEach(p -> out.add(toPayment(p)));
        return out;
    }

    @Override
    public ProviderRefund refund(String paymentId, BigDecimal amount, String idempotencyKey) {
        JsonNode res = send("POST", "/v1/payments/" + paymentId + "/refunds",
                json.writeValueAsString(Map.of("amount", amount)), idempotencyKey);
        return new ProviderRefund(res.path("id").asString(), res.path("status").asString("approved"));
    }

    private ProviderPayment toPayment(JsonNode p) {
        JsonNode amount = p.path("transaction_amount");
        return new ProviderPayment(p.path("id").asString(), p.path("status").asString(""),
                p.path("status_detail").asString(""),
                amount.isMissingNode() || amount.isNull() ? null : amount.decimalValue(),
                p.path("currency_id").asString(""), p.path("external_reference").asString(""));
    }

    private static final class NotFound extends GatewayException {
        NotFound() {
            super("No encontrado");
        }
    }

    private JsonNode send(String method, String path, String body, String idempotencyKey) {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(baseUrl + path))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json");
        if (idempotencyKey != null) {
            b.header("X-Idempotency-Key", idempotencyKey);
        }
        if (body != null) {
            b.header("Content-Type", "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        try {
            HttpResponse<String> res = http.send(b.build(), HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() == 404) {
                throw new NotFound();
            }
            if (res.statusCode() / 100 != 2) {
                // Nunca se registra el token ni el cuerpo completo (puede traer datos del pagador).
                log.warn("Mercado Pago respondió {} a {} {}", res.statusCode(), method, path.replaceAll("\\?.*", ""));
                throw new GatewayException("La pasarela respondió " + res.statusCode());
            }
            return json.readTree(res.body());
        } catch (IOException e) {
            throw new GatewayException("No se pudo conectar con la pasarela", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GatewayException("Conexión interrumpida", e);
        }
    }
}
