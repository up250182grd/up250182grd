package com.englishos.tutor.api;

import java.util.List;

/** Errores recientes del alumno, para personalizar al profesor (texto y voz). */
public interface LearnerErrorsApi {

    /** Pares "original" -> "corrección", del más reciente al más antiguo. */
    List<String> recentPairs(Long userId, int limit);
}
