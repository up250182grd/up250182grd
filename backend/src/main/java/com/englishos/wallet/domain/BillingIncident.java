package com.englishos.wallet.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Situación de cobro que requiere atención humana (sobregiro por encima del tope, datos de uso faltantes...). */
@Entity
@Table(name = "billing_incidents")
public class BillingIncident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 40)
    private String type;

    @Column(name = "user_id", updatable = false)
    private Long userId;

    @Column(name = "ref_type", updatable = false, length = 32)
    private String refType;

    @Column(name = "ref_id", updatable = false, length = 64)
    private String refId;

    @Column(nullable = false, updatable = false, length = 500)
    private String description;

    @Column(nullable = false, length = 16)
    private String status;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected BillingIncident() {
    }

    public static BillingIncident open(String type, Long userId, String refType, String refId, String description,
            Instant now) {
        BillingIncident i = new BillingIncident();
        i.type = type;
        i.userId = userId;
        i.refType = refType;
        i.refId = refId;
        i.description = description.length() > 500 ? description.substring(0, 500) : description;
        i.status = "OPEN";
        i.createdAt = now;
        return i;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRefType() {
        return refType;
    }

    public String getRefId() {
        return refId;
    }

    public String getDescription() {
        return description;
    }

    public String getStatus() {
        return status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
