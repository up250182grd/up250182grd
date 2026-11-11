package com.englishos.vocabulary.api;

import java.time.Instant;
import java.util.List;

/** Lo que recomendaciones y progreso consultan del vocabulario del alumno. */
public interface VocabularyApi {

    record VocabularyStats(int total, int newCards, int learning, int review, int relearning, int mature, int dueNow,
            int newAvailableToday) {
    }

    record ReviewEvent(Instant at, Integer durationMs) {
    }

    /** Agrega al alumno el vocabulario de las lecciones que ya practicó (idempotente). */
    void sync(Long userId);

    VocabularyStats stats(Long userId);

    List<ReviewEvent> reviewsSince(Long userId, Instant from);
}
