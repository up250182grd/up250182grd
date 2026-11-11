package com.englishos.metering.domain;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Tarifa versionada del proveedor (ADR-0005): precio por millón de unidades, con fuente y fecha de verificación. */
@Entity
@Table(name = "provider_pricing_versions")
public class PricingVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "model_id", nullable = false, updatable = false)
    private Long modelId;

    @Column(nullable = false, updatable = false, length = 3)
    private String currency;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String rates;

    @Column(name = "effective_from", nullable = false, updatable = false)
    private Instant effectiveFrom;

    @Column(name = "source_url", nullable = false, updatable = false, length = 500)
    private String sourceUrl;

    @Column(name = "verified_at", nullable = false, updatable = false)
    private LocalDate verifiedAt;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected PricingVersion() {
    }

    public static PricingVersion create(Long modelId, String currency, String ratesJson, Instant effectiveFrom,
            String sourceUrl, LocalDate verifiedAt, Long createdBy, Instant now) {
        PricingVersion p = new PricingVersion();
        p.modelId = modelId;
        p.currency = currency;
        p.rates = ratesJson;
        p.effectiveFrom = effectiveFrom;
        p.sourceUrl = sourceUrl;
        p.verifiedAt = verifiedAt;
        p.createdBy = createdBy;
        p.createdAt = now;
        return p;
    }

    public Long getId() {
        return id;
    }

    public Long getModelId() {
        return modelId;
    }

    public String getCurrency() {
        return currency;
    }

    public String getRatesJson() {
        return rates;
    }

    public Instant getEffectiveFrom() {
        return effectiveFrom;
    }

    public String getSourceUrl() {
        return sourceUrl;
    }

    public LocalDate getVerifiedAt() {
        return verifiedAt;
    }
}
