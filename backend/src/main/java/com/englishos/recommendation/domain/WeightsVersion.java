package com.englishos.recommendation.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Versión de los pesos del recomendador. Inmutable: cada cambio crea una versión nueva (RF-REC-2). */
@Entity
@Table(name = "recommendation_weights")
public class WeightsVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, columnDefinition = "JSON")
    private String weights;

    @Column(nullable = false, updatable = false, length = 500)
    private String note;

    @Column(name = "created_by", updatable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected WeightsVersion() {
    }

    public WeightsVersion(String weightsJson, String note, Long createdBy, Instant now) {
        this.weights = weightsJson;
        this.note = note;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getWeights() {
        return weights;
    }

    public String getNote() {
        return note;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
