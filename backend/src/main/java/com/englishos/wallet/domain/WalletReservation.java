package com.englishos.wallet.domain;

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

/** Saldo apartado para una sesión de IA. Al liquidar se captura lo consumido y se libera el resto. */
@Entity
@Table(name = "wallet_reservations")
public class WalletReservation {

    public enum Status { ACTIVE, SETTLED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "amount_reserved", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountReserved;

    @Column(name = "amount_captured", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountCaptured;

    @Column(name = "amount_released", nullable = false, precision = 19, scale = 2)
    private BigDecimal amountReleased;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected WalletReservation() {
    }

    public static WalletReservation open(Long userId, String subjectType, Long subjectId, Instant now) {
        WalletReservation r = new WalletReservation();
        r.userId = userId;
        r.subjectType = subjectType;
        r.subjectId = subjectId;
        r.amountReserved = BigDecimal.ZERO.setScale(2);
        r.amountCaptured = BigDecimal.ZERO.setScale(2);
        r.amountReleased = BigDecimal.ZERO.setScale(2);
        r.status = Status.ACTIVE;
        r.createdAt = now;
        r.updatedAt = now;
        return r;
    }

    public void increase(BigDecimal amount, Instant now) {
        requireActive();
        amountReserved = amountReserved.add(amount);
        updatedAt = now;
    }

    public void settle(BigDecimal captured, BigDecimal released, Instant now) {
        requireActive();
        amountCaptured = captured;
        amountReleased = released;
        status = Status.SETTLED;
        updatedAt = now;
    }

    private void requireActive() {
        if (status != Status.ACTIVE) {
            throw new IllegalStateException("La reserva ya fue liquidada");
        }
    }

    public Long getId() {
        return id;
    }

    public Long getUserId() {
        return userId;
    }

    public BigDecimal getAmountReserved() {
        return amountReserved;
    }

    public BigDecimal getAmountCaptured() {
        return amountCaptured;
    }

    public Status getStatus() {
        return status;
    }
}
