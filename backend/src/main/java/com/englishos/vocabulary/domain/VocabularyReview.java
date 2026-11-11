package com.englishos.vocabulary.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Historial inmutable de repasos con el estado antes y después (permite migrar de algoritmo, RF-SRS-4). */
@Entity
@Table(name = "vocabulary_reviews")
public class VocabularyReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_id", nullable = false, updatable = false)
    private Long cardId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "client_review_id", nullable = false, updatable = false, length = 64)
    private String clientReviewId;

    @Column(nullable = false, updatable = false)
    private int quality;

    @Column(name = "state_before", nullable = false, updatable = false, columnDefinition = "JSON")
    private String stateBefore;

    @Column(name = "state_after", nullable = false, updatable = false, columnDefinition = "JSON")
    private String stateAfter;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "reviewed_at", nullable = false, updatable = false)
    private Instant reviewedAt;

    protected VocabularyReview() {
    }

    public VocabularyReview(Long cardId, Long userId, String clientReviewId, int quality, String stateBefore,
            String stateAfter, Integer durationMs, Instant reviewedAt) {
        this.cardId = cardId;
        this.userId = userId;
        this.clientReviewId = clientReviewId;
        this.quality = quality;
        this.stateBefore = stateBefore;
        this.stateAfter = stateAfter;
        this.durationMs = durationMs;
        this.reviewedAt = reviewedAt;
    }

    public Long getCardId() {
        return cardId;
    }

    public int getQuality() {
        return quality;
    }

    public Integer getDurationMs() {
        return durationMs;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
