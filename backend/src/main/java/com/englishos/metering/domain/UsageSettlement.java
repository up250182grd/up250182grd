package com.englishos.metering.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.englishos.shared.ids.Ulid;

/**
 * Liquidación de un sujeto (p. ej. una conversación). Conserva todo lo necesario para auditarla: costo del proveedor,
 * tipo de cambio y su fuente, versión de política, factor aplicado, impuestos y asiento contable (RN-4).
 */
@Entity
@Table(name = "usage_settlements")
public class UsageSettlement {

    public enum Status { SETTLED, PENDING_RECONCILIATION, RECONCILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "role_code", nullable = false, updatable = false, length = 32)
    private String roleCode;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(nullable = false, length = 32)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;

    @Column(name = "provider_cost", nullable = false, precision = 24, scale = 10)
    private BigDecimal providerCost;

    @Column(name = "provider_currency", nullable = false, length = 3)
    private String providerCurrency;

    @Column(name = "fx_rate", precision = 18, scale = 6)
    private BigDecimal fxRate;

    @Column(name = "fx_source", length = 32)
    private String fxSource;

    @Column(name = "direct_cost_mxn", precision = 19, scale = 2)
    private BigDecimal directCostMxn;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(name = "factor_applied", nullable = false, updatable = false, precision = 6, scale = 4)
    private BigDecimal factorApplied;

    @Column(name = "tax_mode", nullable = false, updatable = false, length = 16)
    private String taxMode;

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 6, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "price_before_tax_mxn", precision = 19, scale = 2)
    private BigDecimal priceBeforeTaxMxn;

    @Column(name = "markup_mxn", precision = 19, scale = 2)
    private BigDecimal markupMxn;

    @Column(name = "tax_mxn", precision = 19, scale = 2)
    private BigDecimal taxMxn;

    @Column(name = "total_mxn", precision = 19, scale = 2)
    private BigDecimal totalMxn;

    @Column(name = "ledger_transaction_id")
    private Long ledgerTransactionId;

    @Column(length = 500)
    private String note;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "settled_at")
    private Instant settledAt;

    protected UsageSettlement() {
    }

    public static UsageSettlement settled(Long userId, String roleCode, String subjectType, Long subjectId,
            BigDecimal providerCost, String currency, BigDecimal fxRate, String fxSource, CommercialPolicy policy,
            ChargeCalculator.Charge charge, Long ledgerTxId, Instant now) {
        UsageSettlement s = base(userId, roleCode, subjectType, subjectId, providerCost, currency, policy, now);
        s.status = Status.SETTLED;
        s.fxRate = fxRate;
        s.fxSource = fxSource;
        s.directCostMxn = charge.directCostMxn();
        s.priceBeforeTaxMxn = charge.priceBeforeTaxMxn();
        s.markupMxn = charge.markupMxn();
        s.taxMxn = charge.taxMxn();
        s.totalMxn = charge.totalMxn();
        s.ledgerTransactionId = ledgerTxId;
        s.settledAt = now;
        return s;
    }

    /** Sin datos suficientes (p. ej. sin tipo de cambio vigente): no se inventa un costo (RN-17). */
    public static UsageSettlement pending(Long userId, String roleCode, String subjectType, Long subjectId,
            BigDecimal providerCost, String currency, CommercialPolicy policy, String note, Instant now) {
        UsageSettlement s = base(userId, roleCode, subjectType, subjectId, providerCost, currency, policy, now);
        s.status = Status.PENDING_RECONCILIATION;
        s.note = note;
        return s;
    }

    private static UsageSettlement base(Long userId, String roleCode, String subjectType, Long subjectId,
            BigDecimal providerCost, String currency, CommercialPolicy policy, Instant now) {
        UsageSettlement s = new UsageSettlement();
        s.publicId = Ulid.next();
        s.userId = userId;
        s.roleCode = roleCode;
        s.subjectType = subjectType;
        s.subjectId = subjectId;
        s.providerCost = providerCost;
        s.providerCurrency = currency;
        s.policyId = policy.getId();
        s.factorApplied = policy.getFactor();
        s.taxMode = policy.getTaxMode().name();
        s.taxRate = policy.getTaxRate();
        s.createdAt = now;
        return s;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public String getSubjectType() {
        return subjectType;
    }

    public Long getSubjectId() {
        return subjectId;
    }

    public Status getStatus() {
        return status;
    }

    public BigDecimal getProviderCost() {
        return providerCost;
    }

    public String getProviderCurrency() {
        return providerCurrency;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public String getFxSource() {
        return fxSource;
    }

    public BigDecimal getDirectCostMxn() {
        return directCostMxn;
    }

    public BigDecimal getFactorApplied() {
        return factorApplied;
    }

    public BigDecimal getPriceBeforeTaxMxn() {
        return priceBeforeTaxMxn;
    }

    public BigDecimal getMarkupMxn() {
        return markupMxn;
    }

    public BigDecimal getTaxMxn() {
        return taxMxn;
    }

    public BigDecimal getTotalMxn() {
        return totalMxn;
    }

    public String getNote() {
        return note;
    }

    public Instant getSettledAt() {
        return settledAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
