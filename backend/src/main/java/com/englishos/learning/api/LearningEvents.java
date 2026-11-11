package com.englishos.learning.api;

import java.time.Instant;

/**
 * Eventos de dominio del aprendizaje (se publican dentro de la transacción; los oyentes deben actuar tras el
 * commit para no alterar la operación principal).
 */
public final class LearningEvents {

    private LearningEvents() {
    }

    /** Se terminó una ejecución de lección (una sola vez por ejecución). */
    public record LessonRunCompleted(Long userId, String runId, Long lessonId, String state, boolean newlyMastered,
            boolean wasMasteredBefore, Instant at) {
    }

    /** Intento nuevo de un ejercicio (no se publica en reintentos idempotentes). */
    public record ExerciseAttempted(Long userId, String idempotencyKey, Integer durationMs, Instant at) {
    }
}
