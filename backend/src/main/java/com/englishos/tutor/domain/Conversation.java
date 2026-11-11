package com.englishos.tutor.domain;

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

import com.englishos.shared.ids.Ulid;

/**
 * Conversación con el profesor IA. Fija al iniciar el modelo, la versión de tarifa y la política comercial que se
 * usarán al liquidarla (RN-4): un cambio de precio posterior no la afecta.
 */
@Entity
@Table(name = "ai_conversations")
public class Conversation {

    public enum Mode { FREE, GUIDED, ROLEPLAY }

    public enum Status { OPEN, CLOSED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 16)
    private String channel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Mode mode;

    @Column(updatable = false, length = 200)
    private String topic;

    @Column(name = "prompt_version", nullable = false, updatable = false, length = 40)
    private String promptVersion;

    @Column(name = "model_id", nullable = false, updatable = false)
    private Long modelId;

    @Column(name = "pricing_version_id", nullable = false, updatable = false)
    private Long pricingVersionId;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(nullable = false)
    private int turns;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "closed_at")
    private Instant closedAt;

    @Column(name = "close_reason", length = 32)
    private String closeReason;

    @Version
    private int version;

    protected Conversation() {
    }

    public static Conversation start(Long userId, Mode mode, String topic, String promptVersion, Long modelId,
            Long pricingVersionId, Long policyId, Instant now) {
        Conversation c = new Conversation();
        c.publicId = Ulid.next();
        c.userId = userId;
        c.channel = "TEXT";
        c.mode = mode;
        c.topic = topic;
        c.promptVersion = promptVersion;
        c.modelId = modelId;
        c.pricingVersionId = pricingVersionId;
        c.policyId = policyId;
        c.status = Status.OPEN;
        c.startedAt = now;
        c.lastActivityAt = now;
        return c;
    }

    public void recordTurn(Instant now) {
        turns++;
        lastActivityAt = now;
    }

    public void close(String reason, Instant now) {
        if (status == Status.OPEN) {
            status = Status.CLOSED;
            closeReason = reason;
            closedAt = now;
        }
    }

    public boolean isOpen() {
        return status == Status.OPEN;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Mode getMode() {
        return mode;
    }

    public String getTopic() {
        return topic;
    }

    public String getPromptVersion() {
        return promptVersion;
    }

    public Long getModelId() {
        return modelId;
    }

    public Long getPricingVersionId() {
        return pricingVersionId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public Status getStatus() {
        return status;
    }

    public int getTurns() {
        return turns;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public Instant getLastActivityAt() {
        return lastActivityAt;
    }

    public Instant getClosedAt() {
        return closedAt;
    }

    public String getCloseReason() {
        return closeReason;
    }
}
