package com.englishos.tutor.api;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Errores del alumno detectados por el profesor, para personalizar al profesor y para recomendaciones. */
public interface LearnerErrorsApi {

    /** Pares "original" -> "corrección", del más reciente al más antiguo. */
    List<String> recentPairs(Long userId, int limit);

    /** Errores aún no superados, agrupados por categoría (GRAMMAR, VOCABULARY, …). */
    Map<String, Integer> openErrorsByCategory(Long userId);

    /** Momentos en que el alumno escribió al profesor desde {@code from} (para la actividad diaria). */
    List<Instant> tutorMessageTimesSince(Long userId, Instant from);
}
