package com.englishos.gamification.application;

import com.englishos.assessment.api.AssessmentEvents;
import com.englishos.gamification.application.GamificationService.Fact;
import com.englishos.gamification.domain.XpRules;
import com.englishos.learning.api.LearningEvents;
import com.englishos.tutor.api.TutorEvents;
import com.englishos.vocabulary.api.VocabularyEvents;
import com.englishos.voice.api.VoiceEvents;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Traduce eventos de los demás módulos en XP, actividad del día e insignias. Se ejecuta DESPUÉS del commit de la
 * operación original y en su propia transacción: un fallo aquí nunca deshace una lección, un repaso o un cobro (se
 * registra y se sigue). Las entregas repetidas no suman dos veces (id de origen único).
 */
@Component
class GamificationListener {

    private static final Logger log = LoggerFactory.getLogger(GamificationListener.class);
    /** Tiempo típico de un mensaje al profesor cuando no se mide (igual que en la estimación del tablero). */
    private static final long TUTOR_MESSAGE_MS = 60_000;
    private static final long EXERCISE_DEFAULT_MS = 30_000;
    private static final long REVIEW_DEFAULT_MS = 15_000;
    private static final long MAX_EVENT_MS = 300_000;

    private final GamificationService game;

    GamificationListener(GamificationService game) {
        this.game = game;
    }

    private static long ms(Integer measured, long fallback) {
        long v = measured == null || measured <= 0 ? fallback : measured;
        return Math.min(v, MAX_EVENT_MS);
    }

    private void safely(String what, Runnable r) {
        try {
            r.run();
        } catch (RuntimeException e) {
            log.error("Gamificación: no se pudo aplicar {}", what, e);
        }
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(LearningEvents.ExerciseAttempted e) {
        safely("intento", () -> game.apply(Fact.of(e.userId(), "EXERCISE_ATTEMPT", e.idempotencyKey(), e.at())
                .activity(ms(e.durationMs(), EXERCISE_DEFAULT_MS), 0, 0), d -> 0));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(LearningEvents.LessonRunCompleted e) {
        safely("lección", () -> {
            game.apply(Fact.of(e.userId(), "LESSON_RUN", e.runId(), e.at()).ref(String.valueOf(e.lessonId()))
                    .activity(0, 1, 0).counters(1, 0, 0, 0, 0, false),
                    d -> game.lessonRunXp(e.userId(), e.lessonId(), d));
            if (e.newlyMastered()) {
                game.apply(Fact.of(e.userId(), "LESSON_MASTERED", String.valueOf(e.lessonId()), e.at())
                        .counters(0, 1, 0, 0, 0, false), d -> XpRules.LESSON_MASTERED_BONUS);
            }
        });
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(VocabularyEvents.CardReviewed e) {
        safely("repaso", () -> game.apply(Fact.of(e.userId(), "REVIEW", e.reviewId(), e.at())
                .activity(ms(e.durationMs(), REVIEW_DEFAULT_MS), 0, 1)
                .counters(0, 0, e.learnedNow() ? 1 : 0, 0, 0, false), d -> XpRules.REVIEW));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TutorEvents.ErrorResolved e) {
        safely("error superado", () -> game.apply(Fact.of(e.userId(), "ERROR_RESOLVED", e.errorId(), e.at())
                .counters(0, 0, 0, 1, 0, false), d -> XpRules.ERROR_RESOLVED));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TutorEvents.MessageSent e) {
        safely("mensaje", () -> game.apply(Fact.of(e.userId(), "TUTOR_MESSAGE",
                e.conversationId() + ":" + e.at().toEpochMilli(), e.at()).activity(TUTOR_MESSAGE_MS, 0, 0), d -> 0));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(TutorEvents.ConversationClosed e) {
        boolean counts = e.turns() >= XpRules.CONVERSATION_MIN_TURNS;
        safely("conversación", () -> game.apply(Fact.of(e.userId(), "CONVERSATION", e.conversationId(), e.at())
                .counters(0, 0, 0, 0, counts ? 1 : 0, false), d -> counts ? XpRules.CONVERSATION : 0));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(VoiceEvents.SessionEnded e) {
        boolean counts = e.connectedSeconds() >= XpRules.VOICE_MIN_SECONDS;
        safely("llamada", () -> game.apply(Fact.of(e.userId(), "VOICE_CALL", e.sessionId(), e.at())
                .activity(Math.min(e.connectedSeconds(), 3600) * 1000, 0, 0)
                .counters(0, 0, 0, 0, counts ? 1 : 0, false), d -> counts ? XpRules.CONVERSATION : 0));
    }

    @TransactionalEventListener(fallbackExecution = true)
    void on(AssessmentEvents.DiagnosticCompleted e) {
        safely("diagnóstico", () -> game.apply(Fact.of(e.userId(), "DIAGNOSTIC", "first", e.at())
                .counters(0, 0, 0, 0, 0, true), d -> XpRules.DIAGNOSTIC));
    }
}
