package com.englishos.audit;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Entrada inmutable de la bitácora de auditoría. Solo se inserta; nunca se actualiza ni se borra. */
@Entity
@Table(name = "audit_logs")
class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(nullable = false, length = 64, updatable = false)
    private String action;

    @Column(name = "target_type", nullable = false, length = 64, updatable = false)
    private String targetType;

    @Column(name = "target_id", nullable = false, length = 64, updatable = false)
    private String targetId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "before_state", updatable = false)
    private String beforeState;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "after_state", updatable = false)
    private String afterState;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AuditLog() {
    }

    AuditLog(Long actorUserId, String action, String targetType, String targetId, String beforeState,
            String afterState, Instant createdAt) {
        this.actorUserId = actorUserId;
        this.action = action;
        this.targetType = targetType;
        this.targetId = targetId;
        this.beforeState = beforeState;
        this.afterState = afterState;
        this.createdAt = createdAt;
    }

    Long getId() {
        return id;
    }

    Long getActorUserId() {
        return actorUserId;
    }

    String getAction() {
        return action;
    }

    String getTargetType() {
        return targetType;
    }

    String getTargetId() {
        return targetId;
    }

    String getBeforeState() {
        return beforeState;
    }

    String getAfterState() {
        return afterState;
    }

    Instant getCreatedAt() {
        return createdAt;
    }
}
