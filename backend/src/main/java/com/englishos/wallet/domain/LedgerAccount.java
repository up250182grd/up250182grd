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

/** Cuenta del libro. {@code balance} es una caché de Σ líneas, actualizada en la misma transacción (RN-18). */
@Entity
@Table(name = "ledger_accounts")
public class LedgerAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 40)
    private AccountCode code;

    @Column(name = "owner_key", nullable = false, updatable = false)
    private long ownerKey;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private int version;

    protected LedgerAccount() {
    }

    public void apply(BigDecimal amount) {
        balance = balance.add(amount);
    }

    public Long getId() {
        return id;
    }

    public AccountCode getCode() {
        return code;
    }

    public long getOwnerKey() {
        return ownerKey;
    }

    public BigDecimal getBalance() {
        return balance;
    }
}
