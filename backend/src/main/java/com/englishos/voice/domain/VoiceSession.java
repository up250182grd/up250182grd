package com.englishos.voice.domain;

import java.math.BigDecimal;
import java.time.Duration;
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
 * Llamada de voz. Estados: STARTING → ACTIVE → ENDING → ENDED, o FAILED si no llegó a conectarse.
 * {@code activeUserId} solo tiene valor mientras la llamada no termina: la restricción UNIQUE de la base de datos
 * garantiza una sola llamada activa por usuario incluso con solicitudes simultáneas (RF-VOC-8).
 */
@Entity
@Table(name = "voice_sessions")
public class VoiceSession {

    public enum Status { STARTING, ACTIVE, ENDING, ENDED, FAILED }

    public enum Mode { FREE, GUIDED, ROLEPLAY }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Mode mode;

    @Column(updatable = false, length = 200)
    private String topic;

    @Column(name = "model_id", nullable = false, updatable = false)
    private Long modelId;

    @Column(name = "pricing_version_id", nullable = false, updatable = false)
    private Long pricingVersionId;

    @Column(name = "policy_id", nullable = false, updatable = false)
    private Long policyId;

    @Column(nullable = false, updatable = false, length = 32)
    private String provider;

    @Column(name = "provider_call_id", length = 200)
    private String providerCallId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Status status;

    @Column(name = "end_reason", length = 32)
    private String endReason;

    @Column(name = "max_duration_s", nullable = false)
    private int maxDurationSeconds;

    @Column(name = "reserved_mxn", nullable = false, precision = 19, scale = 2)
    private BigDecimal reservedMxn;

    @Column(name = "active_user_id")
    private Long activeUserId;

    @Column(name = "store_transcript", nullable = false, updatable = false)
    private boolean storeTranscript;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "connected_at")
    private Instant connectedAt;

    @Column(name = "last_heartbeat_at")
    private Instant lastHeartbeatAt;

    @Column(name = "last_usage_at")
    private Instant lastUsageAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    private int version;

    protected VoiceSession() {
    }

    public static VoiceSession start(Long userId, Mode mode, String topic, Long modelId, Long pricingVersionId,
            Long policyId, String provider, int maxDurationSeconds, boolean storeTranscript, Instant now) {
        VoiceSession s = new VoiceSession();
        s.publicId = Ulid.next();
        s.userId = userId;
        s.mode = mode;
        s.topic = topic;
        s.modelId = modelId;
        s.pricingVersionId = pricingVersionId;
        s.policyId = policyId;
        s.provider = provider;
        s.status = Status.STARTING;
        s.maxDurationSeconds = maxDurationSeconds;
        s.reservedMxn = BigDecimal.ZERO.setScale(2);
        s.activeUserId = userId;
        s.storeTranscript = storeTranscript;
        s.createdAt = now;
        return s;
    }

    public void connected(String callId, BigDecimal reserved, Instant now) {
        providerCallId = callId;
        reservedMxn = reserved;
        status = Status.ACTIVE;
        connectedAt = now;
        lastHeartbeatAt = now;
    }

    public void reservationExtended(BigDecimal reserved) {
        reservedMxn = reserved;
    }

    public void heartbeat(Instant now) {
        lastHeartbeatAt = now;
    }

    public void usageReceived(Instant now) {
        lastUsageAt = now;
    }

    /** Pide terminar; devuelve false si ya estaba terminando o terminada (idempotente). */
    public boolean beginEnding(String reason) {
        if (status == Status.ACTIVE || status == Status.STARTING) {
            status = Status.ENDING;
            endReason = reason;
            return true;
        }
        return false;
    }

    public void finish(Instant now) {
        if (status != Status.FAILED) {
            status = Status.ENDED;
        }
        activeUserId = null;
        endedAt = now;
    }

    public void fail(String reason, Instant now) {
        status = Status.FAILED;
        endReason = reason;
        activeUserId = null;
        endedAt = now;
    }

    public boolean isLive() {
        return status == Status.ACTIVE;
    }

    /** Tiempo conectado (no es lo mismo que el consumo facturable, RN-35). */
    public long connectedSeconds(Instant now) {
        if (connectedAt == null) {
            return 0;
        }
        return Duration.between(connectedAt, endedAt == null ? now : endedAt).toSeconds();
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

    public Long getModelId() {
        return modelId;
    }

    public Long getPricingVersionId() {
        return pricingVersionId;
    }

    public Long getPolicyId() {
        return policyId;
    }

    public String getProviderCallId() {
        return providerCallId;
    }

    public Status getStatus() {
        return status;
    }

    public String getEndReason() {
        return endReason;
    }

    public int getMaxDurationSeconds() {
        return maxDurationSeconds;
    }

    public BigDecimal getReservedMxn() {
        return reservedMxn;
    }

    public boolean isStoreTranscript() {
        return storeTranscript;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getConnectedAt() {
        return connectedAt;
    }

    public Instant getLastHeartbeatAt() {
        return lastHeartbeatAt;
    }

    public Instant getEndedAt() {
        return endedAt;
    }
}
