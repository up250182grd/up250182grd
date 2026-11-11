package com.englishos.payments.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import com.englishos.shared.ids.Ulid;

/**
 * Intención de recarga. Su monto lo fija el servidor al crearla y es el único que se acredita: un pago del proveedor
 * con otro monto o moneda no acredita nada y queda en revisión.
 */
@Entity
@Table(name = "payment_intents")
public class PaymentIntent {

    public enum Status { CREATED, PENDING, APPROVED, REJECTED, CANCELLED, REVIEW, REFUNDED, PARTIALLY_REFUNDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 24)
    private String provider;

    @Column(name = "amount_mxn", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amountMxn;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "provider_preference_id", length = 100)
    private String providerPreferenceId;

    @Column(name = "provider_payment_id", length = 64)
    private String providerPaymentId;

    @Column(name = "provider_status", length = 40)
    private String providerStatus;

    @Column(name = "checkout_url", length = 500)
    private String checkoutUrl;

    @Column(name = "credited_tx_public_id", length = 26)
    private String creditedTxPublicId;

    @Column(name = "refunded_mxn", nullable = false, precision = 19, scale = 2)
    private BigDecimal refundedMxn;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected PaymentIntent() {
    }

    public static PaymentIntent create(Long userId, String provider, BigDecimal amount, String idempotencyKey,
            Instant now) {
        PaymentIntent p = new PaymentIntent();
        p.publicId = Ulid.next();
        p.userId = userId;
        p.provider = provider;
        p.amountMxn = amount;
        p.currency = "MXN";
        p.status = Status.CREATED;
        p.idempotencyKey = idempotencyKey;
        p.refundedMxn = BigDecimal.ZERO.setScale(2);
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void checkoutCreated(String preferenceId, String url, Instant now) {
        providerPreferenceId = preferenceId;
        checkoutUrl = url;
        updatedAt = now;
    }

    /** El pago del proveedor coincide en monto y moneda con lo que se pidió. */
    public boolean matches(BigDecimal amount, String providerCurrency) {
        return amount != null && amount.compareTo(amountMxn) == 0 && currency.equalsIgnoreCase(providerCurrency);
    }

    public boolean isCredited() {
        return creditedTxPublicId != null;
    }

    public void credited(String paymentId, String providerStatus, String txPublicId, Instant now) {
        this.providerPaymentId = paymentId;
        this.providerStatus = providerStatus;
        this.creditedTxPublicId = txPublicId;
        this.status = Status.APPROVED;
        this.updatedAt = now;
    }

    /** Actualiza el estado sin acreditar (pendiente, rechazado…). Una recarga ya acreditada no retrocede. */
    public void observed(String paymentId, String providerStatus, Status newStatus, Instant now) {
        if (this.providerPaymentId == null) {
            this.providerPaymentId = paymentId;
        }
        this.providerStatus = providerStatus;
        if (!isCredited()) {
            this.status = newStatus;
        }
        this.updatedAt = now;
    }

    public void needsReview(String paymentId, String providerStatus, Instant now) {
        observed(paymentId, providerStatus, Status.REVIEW, now);
    }

    public BigDecimal refundable() {
        return isCredited() ? amountMxn.subtract(refundedMxn) : BigDecimal.ZERO.setScale(2);
    }

    public void refunded(BigDecimal amount, Instant now) {
        refundedMxn = refundedMxn.add(amount);
        status = refundedMxn.compareTo(amountMxn) >= 0 ? Status.REFUNDED : Status.PARTIALLY_REFUNDED;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getProvider() {
        return provider;
    }

    public BigDecimal getAmountMxn() {
        return amountMxn;
    }

    public String getCurrency() {
        return currency;
    }

    public Status getStatus() {
        return status;
    }

    public String getProviderPaymentId() {
        return providerPaymentId;
    }

    public String getProviderStatus() {
        return providerStatus;
    }

    public String getCheckoutUrl() {
        return checkoutUrl;
    }

    public String getCreditedTxPublicId() {
        return creditedTxPublicId;
    }

    public BigDecimal getRefundedMxn() {
        return refundedMxn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
