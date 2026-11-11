package com.englishos.tutor.domain;

import java.time.Instant;

import com.englishos.shared.ids.Ulid;

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

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

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

    @Column(name = "practice_count", nullable = false)
    private int practiceCount;

    @Column(name = "correct_streak", nullable = false)
    private int correctStreak;

    @Column(name = "last_practiced_at")
    private Instant lastPracticedAt;

    @Column(name = "resolved_at")
    private Instant resolvedAt;

    protected LearnerError() {
    }

    public static LearnerError of(Long userId, Long conversationId, ReplyFormat.Correction c, Instant now) {
        LearnerError e = new LearnerError();
        e.publicId = Ulid.next();
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

    /** Aciertos seguidos necesarios para dar el error por superado. */
    public static final int STREAK_TO_RESOLVE = 2;

    /** Registra un intento de corregir la frase; dos aciertos seguidos lo marcan como superado. */
    public void practiced(boolean correct, Instant now) {
        practiceCount++;
        lastPracticedAt = now;
        correctStreak = correct ? correctStreak + 1 : 0;
        if (correctStreak >= STREAK_TO_RESOLVE && resolvedAt == null) {
            resolvedAt = now;
        }
    }

    public boolean isResolved() {
        return resolvedAt != null;
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

    public String getExplanationEs() {
        return explanationEs;
    }

    public String getExplanationEn() {
        return explanationEn;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public int getPracticeCount() {
        return practiceCount;
    }

    public int getCorrectStreak() {
        return correctStreak;
    }

    public Instant getLastPracticedAt() {
        return lastPracticedAt;
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
