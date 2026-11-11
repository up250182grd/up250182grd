package com.englishos.vocabulary.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

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

/** Estado de repetición espaciada de un término para un alumno (RF-SRS-2..4). */
@Entity
@Table(name = "user_vocabulary_cards")
public class VocabularyCard {

    /**
     * NEW: nunca repasada. LEARNING: menos de 2 aciertos seguidos. REVIEW: en intervalos largos.
     * RELEARNING: olvidada en el último repaso (se prioriza en la cola).
     */
    public enum LearningState { NEW, LEARNING, REVIEW, RELEARNING }

    /** Intervalo a partir del cual una tarjeta se considera "madura". */
    public static final int MATURE_DAYS = 21;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "item_id", nullable = false, updatable = false)
    private Long itemId;

    @Column(nullable = false, length = 16)
    private String algorithm;

    @Column(name = "algorithm_version", nullable = false)
    private int algorithmVersion;

    @Column(name = "ease_factor", nullable = false, precision = 5, scale = 3)
    private BigDecimal easeFactor;

    @Column(nullable = false)
    private int repetitions;

    @Column(name = "interval_days", nullable = false)
    private int intervalDays;

    @Enumerated(EnumType.STRING)
    @Column(name = "learning_state", nullable = false, length = 16)
    private LearningState learningState;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "review_count", nullable = false)
    private int reviewCount;

    @Column(name = "first_reviewed_at")
    private Instant firstReviewedAt;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Version
    private int version;

    protected VocabularyCard() {
    }

    public static VocabularyCard create(Long userId, Long itemId, Instant now) {
        VocabularyCard c = new VocabularyCard();
        c.publicId = Ulid.next();
        c.userId = userId;
        c.itemId = itemId;
        c.algorithm = Sm2.ALGORITHM;
        c.algorithmVersion = Sm2.VERSION;
        Sm2.State s = Sm2.State.initial();
        c.easeFactor = s.easeFactor();
        c.repetitions = s.repetitions();
        c.intervalDays = s.intervalDays();
        c.learningState = LearningState.NEW;
        c.dueAt = now;
        c.createdAt = now;
        return c;
    }

    public Sm2.State state() {
        return new Sm2.State(easeFactor, repetitions, intervalDays);
    }

    /**
     * Aplica un repaso. La próxima fecha es el inicio del día local del alumno + intervalo: así una tarjeta "para
     * mañana" aparece desde la mañana, no a la misma hora del repaso.
     */
    public Sm2.State review(int quality, Instant now, ZoneId zone) {
        Sm2.State next = Sm2.next(state(), quality);
        boolean forgot = quality < 3;
        if (forgot && learningState != LearningState.NEW) {
            lapses++;
        }
        easeFactor = next.easeFactor();
        repetitions = next.repetitions();
        intervalDays = next.intervalDays();
        learningState = forgot ? (reviewCount == 0 ? LearningState.LEARNING : LearningState.RELEARNING)
                : repetitions >= 2 ? LearningState.REVIEW : LearningState.LEARNING;
        LocalDate today = LocalDate.ofInstant(now, zone);
        dueAt = today.plusDays(intervalDays).atStartOfDay(zone).toInstant();
        if (firstReviewedAt == null) {
            firstReviewedAt = now;
        }
        lastReviewedAt = now;
        reviewCount++;
        return next;
    }

    public boolean isMature() {
        return intervalDays >= MATURE_DAYS;
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

    public Long getItemId() {
        return itemId;
    }

    public BigDecimal getEaseFactor() {
        return easeFactor;
    }

    public int getRepetitions() {
        return repetitions;
    }

    public int getIntervalDays() {
        return intervalDays;
    }

    public LearningState getLearningState() {
        return learningState;
    }

    public Instant getDueAt() {
        return dueAt;
    }

    public int getLapses() {
        return lapses;
    }

    public int getReviewCount() {
        return reviewCount;
    }

    public Instant getFirstReviewedAt() {
        return firstReviewedAt;
    }

    public Instant getLastReviewedAt() {
        return lastReviewedAt;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public int getAlgorithmVersion() {
        return algorithmVersion;
    }
}
