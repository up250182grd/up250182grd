package com.englishos.metering.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Modelo de IA disponible. {@code simulated = true} identifica al proveedor de pruebas (sin costo real). */
@Entity
@Table(name = "ai_models")
public class AiModel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "model_code", nullable = false, updatable = false, length = 100)
    private String modelCode;

    @Column(nullable = false, updatable = false, length = 16)
    private String modality;

    @Column(name = "display_name", nullable = false, length = 120)
    private String displayName;

    @Column(name = "is_enabled", nullable = false)
    private boolean enabled;

    @Column(name = "is_simulated", nullable = false, updatable = false)
    private boolean simulated;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AiModel() {
    }

    public static AiModel create(String provider, String modelCode, String modality, String displayName, Instant now) {
        AiModel m = new AiModel();
        m.provider = provider;
        m.modelCode = modelCode;
        m.modality = modality;
        m.displayName = displayName;
        m.enabled = true;
        m.simulated = false;
        m.createdAt = now;
        return m;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Long getId() {
        return id;
    }

    public String getProvider() {
        return provider;
    }

    public String getModelCode() {
        return modelCode;
    }

    public String getModality() {
        return modality;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isSimulated() {
        return simulated;
    }
}
