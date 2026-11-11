package com.englishos.payments.web;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.payments.application.PaymentService;
import com.englishos.payments.application.PaymentService.AdminPaymentView;
import com.englishos.payments.application.PaymentService.PaymentsConfig;
import com.englishos.payments.application.PaymentService.TopupView;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class PaymentController {

    private final PaymentService payments;
    private final com.englishos.identity.api.LearnerProfiles profiles;

    PaymentController(PaymentService payments, com.englishos.identity.api.LearnerProfiles profiles) {
        this.payments = payments;
        this.profiles = profiles;
    }

    record GatewayTopupRequest(@NotNull BigDecimal amount, @NotBlank @Size(max = 64) String idempotencyKey) {
    }

    record RefundRequest(@NotNull BigDecimal amount, @NotBlank @Size(max = 500) String note) {
    }

    // ---- Alumno -------------------------------------------------------------------------------

    @GetMapping("/api/v1/payments/config")
    PaymentsConfig config() {
        return payments.config();
    }

    @PostMapping("/api/v1/me/wallet/topups")
    @ResponseStatus(HttpStatus.CREATED)
    TopupView create(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody GatewayTopupRequest req) {
        return payments.createTopup(user, req.amount(), req.idempotencyKey());
    }

    @GetMapping("/api/v1/me/wallet/topups")
    List<TopupView> mine(@AuthenticationPrincipal CurrentUser user) {
        return payments.myTopups(user);
    }

    @GetMapping("/api/v1/me/wallet/topups/{id}")
    TopupView get(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return payments.topup(user, id);
    }

    // ---- Notificación de la pasarela (pública, autenticada por firma) ------------------------

    @PostMapping("/api/v1/webhooks/mercadopago")
    ResponseEntity<Void> webhook(@RequestHeader(name = "x-signature", required = false) String signature,
            @RequestHeader(name = "x-request-id", required = false) String requestId,
            @RequestParam(name = "data.id", required = false) String dataId,
            @RequestParam(name = "type", required = false) String type,
            @RequestBody(required = false) String body) {
        return ResponseEntity.status(payments.handleWebhook(signature, requestId, dataId, type, body)).build();
    }

    // ---- Administración -----------------------------------------------------------------------

    @GetMapping(path = "/api/v1/admin/reports/payments.csv", produces = "text/csv")
    ResponseEntity<byte[]> csv(@AuthenticationPrincipal CurrentUser actor, @RequestParam(required = false) String month) {
        var p = com.englishos.shared.web.CsvResponses.Period.of(month, profiles.zoneOf(actor.id()));
        return com.englishos.shared.web.CsvResponses.csv("recargas-" + p.month() + ".csv",
                payments.paymentsCsv(p.from(), p.to()));
    }

    @GetMapping("/api/v1/admin/payments")
    List<AdminPaymentView> adminList() {
        return payments.adminList();
    }

    @PostMapping("/api/v1/admin/payments/{id}/reconcile")
    AdminPaymentView reconcile(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return payments.reconcile(user, id);
    }

    @PostMapping("/api/v1/admin/payments/{id}/refunds")
    AdminPaymentView refund(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @Valid @RequestBody RefundRequest req) {
        return payments.refund(user, id, req.amount(), req.note());
    }
}
