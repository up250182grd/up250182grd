package com.englishos.tutor.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.englishos.shared.error.ApiException;
import com.englishos.shared.text.AnswerNormalizer;
import com.englishos.tutor.domain.LearnerError;
import com.englishos.tutor.infrastructure.LearnerErrorRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Convierte los errores que el profesor corrigió en práctica (fase 6, "errores recurrentes → práctica"). El alumno
 * reescribe la frase correcta; la evaluación es determinista (sin IA, sin costo). Dos aciertos seguidos lo superan.
 *
 * <p>Orden de la cola: primero las categorías con más errores abiertos (los recurrentes), y dentro de cada una lo menos
 * practicado recientemente.
 */
@Service
public class ErrorPracticeService {

    static final int QUEUE_SIZE = 10;

    private final LearnerErrorRepository errors;
    private final Clock clock;

    ErrorPracticeService(LearnerErrorRepository errors, Clock clock) {
        this.errors = errors;
        this.clock = clock;
    }

    /** No incluye la corrección: el alumno debe producirla. */
    public record ErrorItem(String id, String original, String category, int categoryOpenCount, int correctStreak,
            int streakToResolve, Instant detectedAt) {
    }

    public record ErrorAnswerResult(boolean correct, String expected, String explanationEs, String explanationEn,
            int correctStreak, boolean resolved) {
    }

    @Transactional(readOnly = true)
    public List<ErrorItem> queue(Long userId) {
        List<LearnerError> open = errors.findOpenByUser(userId);
        Map<String, Long> byCategory = open.stream()
                .collect(Collectors.groupingBy(LearnerError::getCategory, Collectors.counting()));
        Comparator<LearnerError> order = Comparator
                .comparing((LearnerError e) -> -byCategory.get(e.getCategory()))
                .thenComparing(e -> e.getLastPracticedAt() == null ? Instant.EPOCH : e.getLastPracticedAt())
                .thenComparing(LearnerError::getId);
        return open.stream().sorted(order).limit(QUEUE_SIZE)
                .map(e -> new ErrorItem(e.getPublicId(), e.getOriginal(), e.getCategory(),
                        byCategory.get(e.getCategory()).intValue(), e.getCorrectStreak(),
                        LearnerError.STREAK_TO_RESOLVE, e.getCreatedAt()))
                .toList();
    }

    @Transactional
    public ErrorAnswerResult answer(Long userId, String errorId, String answer) {
        LearnerError e = errors.findByPublicIdForUpdate(errorId)
                .filter(x -> x.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("ERROR_NOT_FOUND", "Ese error no existe."));
        if (e.isResolved()) {
            throw ApiException.conflict("ERROR_ALREADY_RESOLVED", "Ya superaste este error.");
        }
        boolean correct = !AnswerNormalizer.normalize(answer).isEmpty()
                && AnswerNormalizer.normalize(answer).equals(AnswerNormalizer.normalize(e.getCorrection()));
        e.practiced(correct, Instant.now(clock));
        return new ErrorAnswerResult(correct, e.getCorrection(), e.getExplanationEs(), e.getExplanationEn(),
                e.getCorrectStreak(), e.isResolved());
    }
}
