package com.englishos.assessment.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;

/** Respuesta del diagnóstico: la evidencia en la que se basa la estimación de nivel. */
@Entity
@Table(name = "assessment_responses")
public class AssessmentResponse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, updatable = false)
    private Long sessionId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "item_level", nullable = false, updatable = false, length = 2)
    private CefrLevel itemLevel;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String answer;

    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected AssessmentResponse() {
    }

    public static AssessmentResponse record(Long sessionId, Long itemId, Skill skill, CefrLevel level, String answerJson,
            boolean correct, Instant now) {
        AssessmentResponse r = new AssessmentResponse();
        r.sessionId = sessionId;
        r.itemId = itemId;
        r.skill = skill;
        r.itemLevel = level;
        r.answer = answerJson;
        r.correct = correct;
        r.createdAt = now;
        return r;
    }

    public Long getItemId() {
        return itemId;
    }
}
