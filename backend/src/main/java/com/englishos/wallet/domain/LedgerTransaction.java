package com.englishos.wallet.domain;

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

/** Cabecera inmutable de un asiento. La clave de idempotencia es única: el mismo evento nunca se contabiliza dos veces. */
@Entity
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    public enum Type { TOPUP, RESERVE, CAPTURE, RELEASE, INTERNAL_USAGE, ADJUSTMENT, REFUND }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 32)
    private Type type;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 120)
    private String idempotencyKey;

    @Column(nullable = false, updatable = false, length = 300)
    private String description;

    @Column(name = "ref_type", updatable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", updatable = false, length = 64)
    private String refId;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
    }

    public static LedgerTransaction create(Type type, Long userId, String idempotencyKey, String description,
            String refType, String refId, Long createdBy, Instant now) {
        LedgerTransaction t = new LedgerTransaction();
        t.publicId = Ulid.next();
        t.type = type;
        t.userId = userId;
        t.idempotencyKey = idempotencyKey;
        t.description = description;
        t.refType = refType;
        t.refId = refId;
        t.createdBy = createdBy;
        t.createdAt = now;
        return t;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Type getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public String getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
