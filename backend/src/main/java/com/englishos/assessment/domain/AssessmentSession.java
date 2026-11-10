package com.englishos.assessment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englishos.shared.ids.Ulid;

/**
 * Sesión de diagnóstico. Guarda el estado de la escalera y el ítem presentado actualmente: solo se acepta respuesta
 * para ese ítem (no se puede responder ítems arbitrarios ni repetir uno ya contestado).
 */
@Entity
@Table(name = "assessment_sessions")
public class AssessmentSession {

    public enum Status { IN_PROGRESS, COMPLETED, ABANDONED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, length = 16)
    @jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
    private Status status;

    @Column(name = "algorithm_version", nullable = false, updatable = false, length = 32)
    private String algorithmVersion;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String state;

    @Column(name = "current_item_id")
    private Long currentItemId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column
    private String result;

    @Version
    private int version;

    protected AssessmentSession() {
    }

    public static AssessmentSession start(Long userId, String algorithmVersion, String initialStateJson, Instant now) {
        AssessmentSession s = new AssessmentSession();
        s.publicId = Ulid.next();
        s.userId = userId;
        s.status = Status.IN_PROGRESS;
        s.algorithmVersion = algorithmVersion;
        s.state = initialStateJson;
        s.startedAt = now;
        return s;
    }

    public void present(Long itemId) {
        currentItemId = itemId;
    }

    public void advance(String newStateJson) {
        state = newStateJson;
        currentItemId = null;
    }

    public void complete(String resultJson, Instant now) {
        status = Status.COMPLETED;
        result = resultJson;
        currentItemId = null;
        completedAt = now;
    }

    public void abandon() {
        status = Status.ABANDONED;
        currentItemId = null;
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

    public Status getStatus() {
        return status;
    }

    public String getStateJson() {
        return state;
    }

    public Long getCurrentItemId() {
        return currentItemId;
    }

    public String getResultJson() {
        return result;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
