package com.englishos.learning.api;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Lo que otros módulos (vocabulario, recomendaciones, progreso) pueden consultar del aprendizaje del alumno. */
public interface LearningProgressApi {

    record Term(String term, String translation, String partOfSpeech, String ipa) {
    }

    record LessonVocabulary(String lessonPublicId, CefrLevel level, List<Term> terms) {
    }

    record NextLesson(String publicId, Localized title, CefrLevel level, int estimatedMinutes, String state) {
    }

    record Attempt(Instant at, Integer durationMs) {
    }

    /** Vocabulario de las lecciones que el alumno ya completó al menos una vez. */
    List<LessonVocabulary> vocabularyOfPracticedLessons(Long userId);

    /** Siguiente lección recomendada del plan (la primera no dominada), si hay. */
    Optional<NextLesson> nextLesson(Long userId);

    /** Intentos de ejercicios desde {@code from}. */
    List<Attempt> attemptsSince(Long userId, Instant from);
}
