package com.englishos.metering.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Uso informado por el proveedor para una respuesta concreta. Inmutable e idempotente (RN-21, RN-34). */
@Entity
@Table(name = "ai_usage_records")
public class UsageRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "subject_type", nullable = false, updatable = false, length = 32)
    private String subjectType;

    @Column(name = "subject_id", nullable = false, updatable = false)
    private Long subjectId;

    @Column(name = "model_id", nullable = false, updatable = false)
    private Long modelId;

    @Column(name = "pricing_version_id", nullable = false, updatable = false)
    private Long pricingVersionId;

    @Column(name = "provider_request_id", nullable = false, updatable = false, length = 200)
    private String providerRequestId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String units;

    @Column(name = "provider_cost", nullable = false, updatable = false, precision = 24, scale = 10)
    private BigDecimal providerCost;

    @Column(name = "provider_currency", nullable = false, updatable = false, length = 3)
    private String providerCurrency;

    @Column(name = "data_source", nullable = false, updatable = false, length = 32)
    private String dataSource;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 200)
    private String idempotencyKey;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected UsageRecord() {
    }

    public static UsageRecord of(Long userId, String subjectType, Long subjectId, Long modelId, Long pricingVersionId,
            String providerRequestId, String unitsJson, BigDecimal providerCost, String currency, String dataSource,
            String idempotencyKey, Instant now) {
        UsageRecord u = new UsageRecord();
        u.userId = userId;
        u.subjectType = subjectType;
        u.subjectId = subjectId;
        u.modelId = modelId;
        u.pricingVersionId = pricingVersionId;
        u.providerRequestId = providerRequestId;
        u.units = unitsJson;
        u.providerCost = providerCost;
        u.providerCurrency = currency;
        u.dataSource = dataSource;
        u.idempotencyKey = idempotencyKey;
        u.occurredAt = now;
        return u;
    }

    public BigDecimal getProviderCost() {
        return providerCost;
    }

    public String getProviderCurrency() {
        return providerCurrency;
    }

    public String getUnitsJson() {
        return units;
    }

    public String getDataSource() {
        return dataSource;
    }
}
