package com.englishos.metering.domain;

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

/** Versión inmutable del factor comercial de un rol (RN-1..RN-4). */
@Entity
@Table(name = "commercial_pricing_policies")
public class CommercialPolicy {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "role_code", nullable = false, updatable = false, length = 32)
    private String roleCode;

    @Column(nullable = false, updatable = false, precision = 6, scale = 4)
    private BigDecimal factor;

    @Enumerated(EnumType.STRING)
    @Column(name = "tax_mode", nullable = false, updatable = false, length = 16)
    private ChargeCalculator.TaxMode taxMode;

    @Column(name = "tax_rate", nullable = false, updatable = false, precision = 6, scale = 4)
    private BigDecimal taxRate;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(nullable = false, updatable = false, length = 500)
    private String note;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected CommercialPolicy() {
    }

    public static CommercialPolicy create(String roleCode, BigDecimal factor, ChargeCalculator.TaxMode taxMode,
            BigDecimal taxRate, Instant effectiveFrom, String note, Long createdBy, Instant now) {
        CommercialPolicy p = new CommercialPolicy();
        p.roleCode = roleCode;
        p.factor = factor;
        p.taxMode = taxMode;
        p.taxRate = taxRate;
        p.effectiveFrom = effectiveFrom;
        p.note = note;
        p.createdBy = createdBy;
        p.createdAt = now;
        return p;
    }

    public Long getId() {
        return id;
    }

    public String getRoleCode() {
        return roleCode;
    }

    public BigDecimal getFactor() {
        return factor;
    }

    public ChargeCalculator.TaxMode getTaxMode() {
        return taxMode;
    }

    public BigDecimal getTaxRate() {
        return taxRate;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getNote() {
        return note;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
