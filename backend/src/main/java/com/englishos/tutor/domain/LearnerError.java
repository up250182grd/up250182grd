package com.englishos.tutor.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Error detectado en una conversación (RF-TUT-6); alimenta los "errores recurrentes" y la práctica posterior. */
@Entity
@Table(name = "learner_errors")
public class LearnerError {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "conversation_id", nullable = false, updatable = false)
    private Long conversationId;

    @Column(nullable = false, updatable = false, length = 500)
    private String original;

    @Column(nullable = false, updatable = false, length = 500)
    private String correction;

    @Column(name = "explanation_es", nullable = false, updatable = false, length = 1000)
    private String explanationEs;

    @Column(name = "explanation_en", nullable = false, updatable = false, length = 1000)
    private String explanationEn;

    @Column(nullable = false, updatable = false, length = 40)
    private String category;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LearnerError() {
    }

    public static LearnerError of(Long userId, Long conversationId, ReplyFormat.Correction c, Instant now) {
        LearnerError e = new LearnerError();
        e.userId = userId;
        e.conversationId = conversationId;
        e.original = c.original();
        e.correction = c.correction();
        e.explanationEs = c.explanationEs();
        e.explanationEn = c.explanationEn();
        e.category = c.category();
        e.createdAt = now;
        return e;
    }

    public ReplyFormat.Correction toCorrection() {
        return new ReplyFormat.Correction(original, correction, explanationEs, explanationEn, category);
    }

    public String getCategory() {
        return category;
    }

    public String getOriginal() {
        return original;
    }

    public String getCorrection() {
        return correction;
    }
}
