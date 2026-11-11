package com.englishos.metering.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Tipo de cambio (MXN por unidad de {@code base}) con fuente y fecha (RN-14). */
@Entity
@Table(name = "fx_rates")
public class FxRate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 3)
    private String base;

    @Column(nullable = false, updatable = false, length = 3)
    private String quote;

    @Column(nullable = false, updatable = false, precision = 18, scale = 6)
    private BigDecimal rate;

    @Column(nullable = false, updatable = false, length = 32)
    private String source;

    @Column(name = "rate_date", nullable = false, updatable = false)
    private LocalDate rateDate;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected FxRate() {
    }

    public static FxRate manual(String base, BigDecimal rate, LocalDate date, Long createdBy, Instant now) {
        FxRate f = new FxRate();
        f.base = base;
        f.quote = "MXN";
        f.rate = rate;
        f.source = "MANUAL";
        f.rateDate = date;
        f.createdBy = createdBy;
        f.createdAt = now;
        return f;
    }

    public Long getId() {
        return id;
    }

    public String getBase() {
        return base;
    }

    public BigDecimal getRate() {
        return rate;
    }

    public String getSource() {
        return source;
    }

    public LocalDate getRateDate() {
        return rateDate;
    }
}
