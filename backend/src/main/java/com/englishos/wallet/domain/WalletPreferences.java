package com.englishos.wallet.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Límite de gasto mensual (opcional) y umbral de alerta de saldo bajo definidos por el usuario. */
@Entity
@Table(name = "wallet_preferences")
public class WalletPreferences {

    public static final BigDecimal DEFAULT_LOW_BALANCE = new BigDecimal("30.00");

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "monthly_limit_mxn", precision = 19, scale = 2)
    private BigDecimal monthlyLimitMxn;

    @Column(name = "low_balance_threshold_mxn", nullable = false, precision = 19, scale = 2)
    private BigDecimal lowBalanceThresholdMxn;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected WalletPreferences() {
    }

    public static WalletPreferences defaults(Long userId, Instant now) {
        WalletPreferences p = new WalletPreferences();
        p.userId = userId;
        p.lowBalanceThresholdMxn = DEFAULT_LOW_BALANCE;
        p.updatedAt = now;
        return p;
    }

    public void update(BigDecimal monthlyLimit, BigDecimal lowBalanceThreshold, Instant now) {
        monthlyLimitMxn = monthlyLimit;
        lowBalanceThresholdMxn = lowBalanceThreshold;
        updatedAt = now;
    }

    public BigDecimal getMonthlyLimitMxn() {
        return monthlyLimitMxn;
    }

    public BigDecimal getLowBalanceThresholdMxn() {
        return lowBalanceThresholdMxn;
    }
}
