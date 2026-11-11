package com.englishos.vocabulary.api;

import java.time.Instant;

/** Eventos de dominio del vocabulario. */
public final class VocabularyEvents {

    private VocabularyEvents() {
    }

    /**
     * Repaso nuevo (no se publica en reintentos idempotentes). {@code learnedNow}: la tarjeta llegó por primera vez
     * a repaso largo (2 aciertos seguidos).
     */
    public record CardReviewed(Long userId, String reviewId, int quality, Integer durationMs, boolean learnedNow,
            Instant at) {
    }
}
