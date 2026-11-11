package com.englishos.gamification.domain;

import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Insignias por hitos de aprendizaje real (RF-GAM-3). Cada una tiene una meta medible; una vez ganada no se pierde.
 */
public final class Badges {

    private Badges() {
    }

    /** Contadores del alumno (solo de actividades completadas). */
    public record Stats(int lessonsCompleted, int lessonsMastered, int wordsLearned, int errorsResolved,
            int conversations, boolean diagnosticDone, int bestStreak) {
    }

    public record Badge(String code, String title, String description, int goal, ToIntFunction<Stats> progress) {

        public boolean earned(Stats s) {
            return progress.applyAsInt(s) >= goal;
        }

        public int progressOf(Stats s) {
            return Math.min(goal, progress.applyAsInt(s));
        }
    }

    public static final List<Badge> ALL = List.of(
            new Badge("DIAGNOSTIC", "Punto de partida", "Completa el diagnóstico de nivel.", 1,
                    s -> s.diagnosticDone() ? 1 : 0),
            new Badge("FIRST_LESSON", "Primer paso", "Termina tu primera lección.", 1, Stats::lessonsCompleted),
            new Badge("FIRST_MASTERY", "Dominado", "Domina una lección (buen resultado en dos días distintos).", 1,
                    Stats::lessonsMastered),
            new Badge("MASTERED_5", "Base sólida", "Domina 5 lecciones.", 5, Stats::lessonsMastered),
            new Badge("WORDS_10", "Vocabulario en marcha", "Lleva 10 palabras a repaso largo.", 10,
                    Stats::wordsLearned),
            new Badge("ERRORS_5", "Aprender del error", "Supera 5 errores que te corrigió el profesor.", 5,
                    Stats::errorsResolved),
            new Badge("FIRST_CONVERSATION", "Rompe el hielo", "Ten una conversación completa con el profesor IA.", 1,
                    Stats::conversations),
            new Badge("STREAK_7", "Una semana seguida", "Alcanza una racha de 7 días.", 7, Stats::bestStreak),
            new Badge("STREAK_30", "Hábito formado", "Alcanza una racha de 30 días.", 30, Stats::bestStreak));
}
