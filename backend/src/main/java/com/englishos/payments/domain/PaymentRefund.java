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

import com.englishos.shared.ids.Ulid;

/** Devolución manual de saldo de una recarga, solicitada por un administrador. */
@Entity
@Table(name = "payment_refunds")
public class PaymentRefund {

    public enum Status { PENDING, COMPLETED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "intent_id", nullable = false, updatable = false)
    private Long intentId;

    @Column(name = "amount_mxn", nullable = false, updatable = false, precision = 19, scale = 2)
    private BigDecimal amountMxn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "provider_refund_id", length = 64)
    private String providerRefundId;

    @Column(nullable = false, updatable = false, length = 500)
    private String note;

    @Column(name = "requested_by", nullable = false, updatable = false)
    private Long requestedBy;

    @Column(length = 500)
    private String failure;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected PaymentRefund() {
    }

    public static PaymentRefund request(Long intentId, BigDecimal amount, String note, Long by, Instant now) {
        PaymentRefund r = new PaymentRefund();
        r.publicId = Ulid.next();
        r.intentId = intentId;
        r.amountMxn = amount;
        r.status = Status.PENDING;
        r.note = note;
        r.requestedBy = by;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void completed(String providerRefundId, Instant now) {
        this.providerRefundId = providerRefundId;
        this.status = Status.COMPLETED;
        this.updatedAt = now;
    }

    public void failed(String reason, Instant now) {
        this.failure = reason.length() > 500 ? reason.substring(0, 500) : reason;
        this.status = Status.FAILED;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getIntentId() {
        return intentId;
    }

    public BigDecimal getAmountMxn() {
        return amountMxn;
    }

    public Status getStatus() {
        return status;
    }

    public String getProviderRefundId() {
        return providerRefundId;
    }

    public String getNote() {
        return note;
    }

    public String getFailure() {
        return failure;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
