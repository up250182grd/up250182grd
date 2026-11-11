package com.englishos.payments.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.payments.domain.PaymentGateway;
import com.englishos.payments.domain.PaymentGateway.GatewayException;
import com.englishos.payments.domain.PaymentGateway.ProviderPayment;
import com.englishos.payments.domain.PaymentIntent;
import com.englishos.payments.domain.PaymentRefund;
import com.englishos.payments.domain.WebhookEvent;
import com.englishos.payments.domain.WebhookSignature;
import com.englishos.payments.infrastructure.PaymentIntentRepository;
import com.englishos.payments.infrastructure.PaymentRefundRepository;
import com.englishos.payments.infrastructure.WebhookEventRepository;
import com.englishos.shared.error.ApiException;
import com.englishos.wallet.api.WalletApi;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Recargas con pasarela (Fase 7). Reglas de dinero:
 * <ol>
 * <li>El monto lo fija el servidor al crear la intención; el navegador solo elige cuánto recargar.</li>
 * <li>El regreso desde la pasarela NO acredita: solo muestra el estado.</li>
 * <li>Una notificación con firma inválida se registra y se descarta.</li>
 * <li>Con firma válida, el servidor consulta el pago en la API del proveedor y acredita solo si está aprobado y el
 * monto y la moneda coinciden con la intención. Si no coinciden: revisión manual e incidencia, sin crédito.</li>
 * <li>El crédito es idempotente por pago del proveedor (eventos repetidos o en paralelo no duplican saldo).</li>
 * </ol>
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);
    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9-]{8,64}");

    private final PaymentIntentRepository intents;
    private final PaymentRefundRepository refunds;
    private final WebhookEventRepository events;
    private final ObjectProvider<PaymentGateway> gatewayProvider;
    private final WalletApi wallet;
    private final LearnerProfiles profiles;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;
    private final TransactionTemplate tx;
    private final String webhookSecret;
    private final String baseUrl;
    private final String notificationUrl;
    private final BigDecimal minTopup;
    private final BigDecimal maxTopup;

    PaymentService(PaymentIntentRepository intents, PaymentRefundRepository refunds, WebhookEventRepository events,
            ObjectProvider<PaymentGateway> gatewayProvider, WalletApi wallet, LearnerProfiles profiles,
            AuditService audit, JsonMapper json, Clock clock, TransactionTemplate tx,
            @Value("${app.payments.mercadopago.webhook-secret:}") String webhookSecret,
            @Value("${app.base-url}") String baseUrl,
            @Value("${app.payments.notification-url:}") String notificationUrl,
            @Value("${app.billing.min-topup-mxn:100.00}") BigDecimal minTopup,
            @Value("${app.billing.max-topup-mxn:2000.00}") BigDecimal maxTopup) {
        this.intents = intents;
        this.refunds = refunds;
        this.events = events;
        this.gatewayProvider = gatewayProvider;
        this.wallet = wallet;
        this.profiles = profiles;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
        this.tx = tx;
        this.webhookSecret = webhookSecret;
        this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.notificationUrl = notificationUrl;
        this.minTopup = minTopup;
        this.maxTopup = maxTopup;
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record PaymentsConfig(boolean enabled, String provider, boolean testMode, BigDecimal minTopupMxn,
            BigDecimal maxTopupMxn) {
    }

    public record TopupView(String id, BigDecimal amountMxn, String currency, String status, String providerStatus,
            String checkoutUrl, boolean credited, BigDecimal refundedMxn, Instant createdAt, Instant updatedAt) {
    }

    public record RefundView(String id, BigDecimal amountMxn, String status, String providerRefundId, String note,
            String failure, Instant createdAt) {
    }

    public record AdminPaymentView(String id, Long userId, String provider, BigDecimal amountMxn, String status,
            String providerPaymentId, String providerStatus, boolean credited, BigDecimal refundedMxn,
            BigDecimal refundableMxn, List<RefundView> refunds, Instant createdAt, Instant updatedAt) {
    }

    /** Resultado de procesar un pago del proveedor (para la bitácora del webhook y la conciliación). */
    public record ApplyOutcome(String outcome, String detail) {
    }

    // ---- Alumno -------------------------------------------------------------------------------

    public PaymentsConfig config() {
        PaymentGateway g = gatewayProvider.getIfAvailable();
        return new PaymentsConfig(g != null, g == null ? null : g.provider(), g != null && g.testMode(), minTopup,
                maxTopup);
    }

    public TopupView createTopup(CurrentUser user, BigDecimal amount, String idempotencyKey) {
        PaymentGateway gateway = requireGateway();
        if (!profiles.isEmailVerified(user.id())) {
            throw ApiException.forbidden("EMAIL_NOT_VERIFIED", "Confirma tu correo antes de recargar.");
        }
        if (idempotencyKey == null || !IDEMPOTENCY_KEY.matcher(idempotencyKey).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY", "Falta una clave de idempotencia válida.");
        }
        if (amount == null || amount.scale() > 2 || amount.compareTo(minTopup) < 0 || amount.compareTo(maxTopup) > 0) {
            throw ApiException.badRequest("INVALID_TOPUP_AMOUNT",
                    "La recarga debe ser de " + minTopup + " a " + maxTopup + " MXN, con hasta 2 decimales.");
        }
        Optional<PaymentIntent> existing = intents.findByUserIdAndIdempotencyKey(user.id(), idempotencyKey);
        if (existing.isPresent() && existing.get().getCheckoutUrl() != null) {
            return view(existing.get()); // reintento del mismo clic
        }
        PaymentIntent intent = existing.orElseGet(() -> tx.execute(st -> intents.saveAndFlush(PaymentIntent.create(
                user.id(), gateway.provider(), amount.setScale(2), idempotencyKey, Instant.now(clock)))));
        String back = baseUrl + "/app/wallet/topups/" + intent.getPublicId();
        PaymentGateway.Checkout checkout;
        try {
            checkout = gateway.createCheckout(new PaymentGateway.CheckoutRequest(intent.getPublicId(),
                    intent.getAmountMxn(), intent.getCurrency(), "Recarga de saldo EnglishOS", user.email(), back,
                    back, back, notificationUrl));
        } catch (GatewayException e) {
            log.warn("No se pudo crear el pago {}: {}", intent.getPublicId(), e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR",
                    "No pudimos conectar con la pasarela de pagos. No se cobró nada; inténtalo de nuevo.");
        }
        return tx.execute(st -> {
            PaymentIntent p = intents.findByPublicIdForUpdate(intent.getPublicId()).orElseThrow();
            p.checkoutCreated(checkout.preferenceId(), checkout.checkoutUrl(), Instant.now(clock));
            audit.record(user.id(), "PAYMENT_INTENT_CREATED", "PAYMENT_INTENT", p.getPublicId(), null,
                    Map.of("amount", p.getAmountMxn().toPlainString(), "provider", p.getProvider()));
            return view(p);
        });
    }

    /** Estado de una recarga del alumno. Consultarla NUNCA acredita: solo la notificación verificada lo hace. */
    public TopupView topup(CurrentUser user, String id) {
        return intents.findByPublicId(id).filter(p -> p.getUserId().equals(user.id())).map(PaymentService::view)
                .orElseThrow(() -> ApiException.notFound("TOPUP_NOT_FOUND", "Recarga no encontrada."));
    }

    public List<TopupView> myTopups(CurrentUser user) {
        return intents.findRecentByUser(user.id(), PageRequest.of(0, 20)).stream().map(PaymentService::view).toList();
    }

    // ---- Notificaciones -----------------------------------------------------------------------

    /** Devuelve el código HTTP: 200 procesada o ignorada, 401 firma inválida, 503 para que la pasarela reintente. */
    public int handleWebhook(String signature, String requestId, String queryDataId, String queryType, String body) {
        JsonNode payload = parse(body);
        String dataId = firstNonBlank(queryDataId, payload.path("data").path("id").asString(null));
        String type = firstNonBlank(queryType, payload.path("type").asString(null));
        String action = payload.path("action").asString(null);
        boolean valid = WebhookSignature.isValid(webhookSecret, signature, requestId, dataId);
        PaymentGateway gateway = gatewayProvider.getIfAvailable();
        String provider = gateway == null ? "MERCADOPAGO" : gateway.provider();

        WebhookEvent event = tx.execute(st -> events.save(WebhookEvent.received(provider, requestId, type, action,
                dataId, valid, body, Instant.now(clock))));
        if (!valid) {
            finish(event, "INVALID_SIGNATURE", "Firma ausente o inválida: no se procesó.");
            return 401;
        }
        if (gateway == null) {
            finish(event, "PAYMENTS_DISABLED", "Pasarela no configurada.");
            return 200;
        }
        if (!"payment".equals(type) || dataId == null) {
            finish(event, "IGNORED", "Tipo de notificación sin efecto: " + type);
            return 200;
        }
        try {
            Optional<ProviderPayment> payment = gateway.fetchPayment(dataId);
            if (payment.isEmpty()) {
                finish(event, "PAYMENT_NOT_FOUND", "La pasarela no reconoce el pago " + dataId);
                return 200;
            }
            ApplyOutcome outcome = apply(payment.get());
            finish(event, outcome.outcome(), outcome.detail());
            return 200;
        } catch (GatewayException e) {
            finish(event, "GATEWAY_ERROR", e.getMessage());
            return 503;
        }
    }

    /** Aplica el estado de un pago consultado al proveedor. Es la ÚNICA vía para acreditar saldo. */
    public ApplyOutcome apply(ProviderPayment p) {
        return tx.execute(st -> {
            Instant now = Instant.now(clock);
            Optional<PaymentIntent> found = p.externalReference() == null ? Optional.empty()
                    : intents.findByPublicIdForUpdate(p.externalReference());
            if (found.isEmpty()) {
                wallet.openIncident("UNKNOWN_PAYMENT", null, "PROVIDER_PAYMENT", p.id(),
                        "Pago del proveedor sin recarga asociada (referencia: " + p.externalReference() + ").");
                return new ApplyOutcome("UNKNOWN_REFERENCE", "Sin intención para " + p.externalReference());
            }
            PaymentIntent intent = found.get();
            if (intent.isCredited() && !p.id().equals(intent.getProviderPaymentId())) {
                wallet.openIncident("DUPLICATE_PAYMENT", intent.getUserId(), "PAYMENT_INTENT", intent.getPublicId(),
                        "Segundo pago (" + p.id() + ") para una recarga ya acreditada: revisar y devolver.");
                return new ApplyOutcome("DUPLICATE_PAYMENT", "Recarga ya acreditada con otro pago");
            }
            switch (p.status()) {
                case "approved" -> {
                    if (!intent.matches(p.amount(), p.currency())) {
                        intent.needsReview(p.id(), p.status(), now);
                        wallet.openIncident("AMOUNT_MISMATCH", intent.getUserId(), "PAYMENT_INTENT",
                                intent.getPublicId(), "El pago " + p.id() + " es de " + p.amount() + " "
                                        + p.currency() + " y la recarga es de " + intent.getAmountMxn()
                                        + " MXN. No se acreditó.");
                        return new ApplyOutcome("AMOUNT_MISMATCH", "Monto o moneda distintos: sin crédito");
                    }
                    if (intent.isCredited()) {
                        return new ApplyOutcome("ALREADY_CREDITED", "Pago ya acreditado");
                    }
                    WalletApi.LedgerResult r = wallet.creditGatewayTopup(intent.getUserId(), intent.getAmountMxn(),
                            intent.getProvider(), p.id(), intent.getPublicId());
                    intent.credited(p.id(), p.status(), r.transactionId(), now);
                    return new ApplyOutcome("CREDITED", "Acreditados " + intent.getAmountMxn() + " MXN");
                }
                case "pending", "in_process", "authorized", "in_mediation" -> {
                    intent.observed(p.id(), p.status(), PaymentIntent.Status.PENDING, now);
                    return new ApplyOutcome("PENDING", p.status());
                }
                case "rejected" -> {
                    intent.observed(p.id(), p.status(), PaymentIntent.Status.REJECTED, now);
                    return new ApplyOutcome("REJECTED", p.statusDetail());
                }
                case "cancelled" -> {
                    intent.observed(p.id(), p.status(), PaymentIntent.Status.CANCELLED, now);
                    return new ApplyOutcome("CANCELLED", p.statusDetail());
                }
                case "refunded", "charged_back" -> {
                    if (intent.isCredited() && intent.getRefundedMxn().compareTo(intent.getAmountMxn()) < 0) {
                        wallet.openIncident("GATEWAY_REVERSAL", intent.getUserId(), "PAYMENT_INTENT",
                                intent.getPublicId(), "La pasarela informa " + p.status() + " de un pago acreditado "
                                        + "que no se devolvió desde EnglishOS: revisar el saldo del alumno.");
                    }
                    intent.observed(p.id(), p.status(), intent.getStatus(), now);
                    return new ApplyOutcome("REVERSAL_REPORTED", p.status());
                }
                default -> {
                    intent.observed(p.id(), p.status(), intent.getStatus(), now);
                    return new ApplyOutcome("UNKNOWN_STATUS", p.status());
                }
            }
        });
    }

    // ---- Administración -----------------------------------------------------------------------

    @PreAuthorize("hasAuthority('billing:read:any')")
    public List<AdminPaymentView> adminList() {
        return intents.findRecent(PageRequest.of(0, 100)).stream().map(this::adminView).toList();
    }

    /** Conciliación manual: pregunta a la pasarela por los pagos de una recarga (si no llegó la notificación). */
    @PreAuthorize("hasAuthority('payments:refund')")
    public AdminPaymentView reconcile(CurrentUser actor, String intentId) {
        PaymentGateway gateway = requireGateway();
        PaymentIntent intent = intents.findByPublicId(intentId)
                .orElseThrow(() -> ApiException.notFound("TOPUP_NOT_FOUND", "Recarga no encontrada."));
        List<ProviderPayment> payments;
        try {
            payments = gateway.searchByExternalReference(intent.getPublicId());
        } catch (GatewayException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "PAYMENT_GATEWAY_ERROR", "La pasarela no respondió.");
        }
        payments.forEach(this::apply);
        tx.executeWithoutResult(st -> audit.record(actor.id(), "PAYMENT_RECONCILED", "PAYMENT_INTENT", intentId,
                null, Map.of("paymentsFound", payments.size())));
        return adminView(intents.findByPublicId(intentId).orElseThrow());
    }

    /** Concilia las recargas abiertas recientes (la llama una tarea programada). */
    public int reconcileOpen() {
        PaymentGateway gateway = gatewayProvider.getIfAvailable();
        if (gateway == null) {
            return 0;
        }
        Instant now = Instant.now(clock);
        int applied = 0;
        for (PaymentIntent p : intents.findByStatusInAndCreatedAtBetween(
                List.of(PaymentIntent.Status.CREATED, PaymentIntent.Status.PENDING), now.minus(Duration.ofDays(3)),
                now.minus(Duration.ofMinutes(5)))) {
            try {
                for (ProviderPayment pp : gateway.searchByExternalReference(p.getPublicId())) {
                    apply(pp);
                    applied++;
                }
            } catch (GatewayException e) {
                log.warn("Conciliación de {} pendiente: {}", p.getPublicId(), e.getMessage());
            }
        }
        return applied;
    }

    /**
     * Devolución manual de saldo no usado. Primero se retira el saldo (no puede quedar negativo), después se pide la
     * devolución a la pasarela; si la pasarela la rechaza, el saldo se repone y se abre una incidencia.
     */
    @PreAuthorize("hasAuthority('payments:refund')")
    public AdminPaymentView refund(CurrentUser actor, String intentId, BigDecimal amount, String note) {
        PaymentGateway gateway = requireGateway();
        if (note == null || note.strip().length() < 5 || note.length() > 500) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Explica el motivo de la devolución (5 a 500 caracteres).");
        }
        if (amount == null || amount.signum() <= 0 || amount.scale() > 2) {
            throw ApiException.badRequest("INVALID_REFUND_AMOUNT", "El monto debe ser positivo, con hasta 2 decimales.");
        }
        record Prepared(PaymentRefund refund, PaymentIntent intent) {
        }
        Prepared prep = tx.execute(st -> {
            PaymentIntent p = intents.findByPublicIdForUpdate(intentId)
                    .orElseThrow(() -> ApiException.notFound("TOPUP_NOT_FOUND", "Recarga no encontrada."));
            BigDecimal pending = refunds.findByIntentIdOrderByIdAsc(p.getId()).stream()
                    .filter(r -> r.getStatus() == PaymentRefund.Status.PENDING)
                    .map(PaymentRefund::getAmountMxn).reduce(BigDecimal.ZERO, BigDecimal::add);
            if (amount.compareTo(p.refundable().subtract(pending)) > 0) {
                throw ApiException.badRequest("REFUND_EXCEEDS_TOPUP",
                        "Solo se puede devolver hasta " + p.refundable().subtract(pending) + " MXN de esta recarga.");
            }
            PaymentRefund r = refunds.save(PaymentRefund.request(p.getId(), amount.setScale(2), note.strip(),
                    actor.id(), Instant.now(clock)));
            try {
                wallet.debitGatewayRefund(p.getUserId(), r.getAmountMxn(), p.getProvider(), r.getPublicId(), actor.id());
            } catch (ApiException e) {
                throw ApiException.conflict("INSUFFICIENT_BALANCE_FOR_REFUND",
                        "El alumno ya usó parte de ese saldo: su saldo disponible no alcanza para esta devolución.");
            }
            audit.record(actor.id(), "PAYMENT_REFUND_REQUESTED", "PAYMENT_INTENT", p.getPublicId(), null,
                    Map.of("amount", r.getAmountMxn().toPlainString(), "refund", r.getPublicId(), "note", r.getNote()));
            return new Prepared(r, p);
        });
        PaymentGateway.ProviderRefund pr;
        try {
            pr = gateway.refund(prep.intent().getProviderPaymentId(), prep.refund().getAmountMxn(),
                    prep.refund().getPublicId());
        } catch (GatewayException e) {
            rollBackRefund(actor, intentId, prep.refund(), prep.intent(), e);
            throw new ApiException(HttpStatus.BAD_GATEWAY, "REFUND_FAILED",
                    "La pasarela rechazó la devolución. El saldo del alumno se repuso y quedó una incidencia.");
        }
        try {
            tx.executeWithoutResult(st -> {
                PaymentRefund r = refunds.findById(prep.refund().getId()).orElseThrow();
                r.completed(pr.id(), Instant.now(clock));
                intents.findByPublicIdForUpdate(intentId).orElseThrow().refunded(r.getAmountMxn(), Instant.now(clock));
                audit.record(actor.id(), "PAYMENT_REFUND_COMPLETED", "PAYMENT_INTENT", intentId, null,
                        Map.of("refund", r.getPublicId(), "providerRefund", String.valueOf(pr.id())));
            });
        } catch (RuntimeException e) {
            // La pasarela YA devolvió el dinero pero no se pudo registrar aquí: no se repone el saldo (sería pagar
            // dos veces); se deja constancia para revisión manual.
            log.error("Devolución {} hecha en la pasarela pero no registrada", prep.refund().getPublicId(), e);
            tx.executeWithoutResult(st -> wallet.openIncident("REFUND_STATE_UNKNOWN", prep.intent().getUserId(),
                    "PAYMENT_INTENT", intentId, "La pasarela confirmó la devolución " + prep.refund().getPublicId()
                            + " (" + pr.id() + ") pero no se pudo registrar: revisar el estado."));
            throw e;
        }
        return adminView(intents.findByPublicId(intentId).orElseThrow());
    }

    private void rollBackRefund(CurrentUser actor, String intentId, PaymentRefund refund, PaymentIntent intent,
            GatewayException cause) {
        tx.executeWithoutResult(st -> {
            PaymentRefund r = refunds.findById(refund.getId()).orElseThrow();
            r.failed(cause.getMessage(), Instant.now(clock));
            wallet.reverseGatewayRefund(intent.getUserId(), r.getAmountMxn(), intent.getProvider(), r.getPublicId(),
                    actor.id());
            wallet.openIncident("REFUND_FAILED", intent.getUserId(), "PAYMENT_INTENT", intentId,
                    "La pasarela rechazó la devolución " + r.getPublicId() + "; el saldo se repuso.");
        });
    }

    @PreAuthorize("hasAuthority('billing:read:any')")
    public String paymentsCsv(Instant from, Instant to) {
        StringBuilder sb = new StringBuilder(com.englishos.shared.text.Csv.BOM);
        sb.append(com.englishos.shared.text.Csv.row(List.of("creada_utc", "recarga", "usuario_id", "pasarela",
                "monto_mxn", "estado", "pago_pasarela", "estado_pasarela", "acreditada", "asiento_credito",
                "devuelto_mxn")));
        for (PaymentIntent p : intents.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByIdAsc(from, to)) {
            sb.append(com.englishos.shared.text.Csv.row(java.util.Arrays.asList(p.getCreatedAt(), p.getPublicId(),
                    p.getUserId(), p.getProvider(), p.getAmountMxn(), p.getStatus().name(), p.getProviderPaymentId(),
                    p.getProviderStatus(), p.isCredited(), p.getCreditedTxPublicId(), p.getRefundedMxn())));
        }
        return sb.toString();
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private PaymentGateway requireGateway() {
        PaymentGateway g = gatewayProvider.getIfAvailable();
        if (g == null) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "PAYMENTS_DISABLED",
                    "Las recargas con pasarela no están disponibles en este entorno.");
        }
        return g;
    }

    private void finish(WebhookEvent event, String outcome, String detail) {
        tx.executeWithoutResult(st -> events.findById(event.getId()).ifPresent(e -> e.outcome(outcome, detail)));
    }

    private JsonNode parse(String body) {
        try {
            return body == null || body.isBlank() ? json.createObjectNode() : json.readTree(body);
        } catch (RuntimeException e) {
            return json.createObjectNode();
        }
    }

    private static String firstNonBlank(String a, String b) {
        return a != null && !a.isBlank() ? a : b != null && !b.isBlank() ? b : null;
    }

    static TopupView view(PaymentIntent p) {
        return new TopupView(p.getPublicId(), p.getAmountMxn(), p.getCurrency(), p.getStatus().name(),
                p.getProviderStatus(), p.getCheckoutUrl(), p.isCredited(), p.getRefundedMxn(), p.getCreatedAt(),
                p.getUpdatedAt());
    }

    private AdminPaymentView adminView(PaymentIntent p) {
        List<RefundView> rs = refunds.findByIntentIdOrderByIdAsc(p.getId()).stream()
                .map(r -> new RefundView(r.getPublicId(), r.getAmountMxn(), r.getStatus().name(),
                        r.getProviderRefundId(), r.getNote(), r.getFailure(), r.getCreatedAt()))
                .toList();
        return new AdminPaymentView(p.getPublicId(), p.getUserId(), p.getProvider(), p.getAmountMxn(),
                p.getStatus().name(), p.getProviderPaymentId(), p.getProviderStatus(), p.isCredited(),
                p.getRefundedMxn(), p.refundable(), rs, p.getCreatedAt(), p.getUpdatedAt());
    }
}
