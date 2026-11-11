package com.englishos.payments.domain;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** Puerto hacia la pasarela de pagos. El adaptador real es Mercado Pago (modo prueba). */
public interface PaymentGateway {

    String provider();

    /** True si opera con credenciales de prueba: ningún cobro es real. */
    boolean testMode();

    record CheckoutRequest(String intentId, BigDecimal amount, String currency, String title, String payerEmail,
            String successUrl, String pendingUrl, String failureUrl, String notificationUrl) {
    }

    record Checkout(String preferenceId, String checkoutUrl) {
    }

    /** Estado de un pago según el proveedor (fuente de verdad para acreditar). */
    record ProviderPayment(String id, String status, String statusDetail, BigDecimal amount, String currency,
            String externalReference) {
    }

    record ProviderRefund(String id, String status) {
    }

    Checkout createCheckout(CheckoutRequest request);

    Optional<ProviderPayment> fetchPayment(String paymentId);

    /** Pagos asociados a una intención (para conciliar cuando no llegó la notificación). */
    List<ProviderPayment> searchByExternalReference(String externalReference);

    ProviderRefund refund(String paymentId, BigDecimal amount, String idempotencyKey);

    class GatewayException extends RuntimeException {

        public GatewayException(String message) {
            super(message);
        }

        public GatewayException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
