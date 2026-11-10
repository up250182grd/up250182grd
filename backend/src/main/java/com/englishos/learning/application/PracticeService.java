package com.englishos.learning.application;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import com.englishos.identity.api.LearnerProfiles;
import com.englishos.learning.api.Localized;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Exercise;
import com.englishos.learning.domain.ExerciseAttempt;
import com.englishos.learning.domain.ExerciseEvaluator;
import com.englishos.learning.domain.InvalidExerciseException;
import com.englishos.learning.domain.Lesson;
import com.englishos.learning.domain.LessonProgress;
import com.englishos.learning.domain.LessonRun;
import com.englishos.learning.domain.MasteryPolicy;
import com.englishos.learning.infrastructure.ExerciseAttemptRepository;
import com.englishos.learning.infrastructure.ExerciseRepository;
import com.englishos.learning.infrastructure.LessonProgressRepository;
import com.englishos.learning.infrastructure.LessonRepository;
import com.englishos.learning.infrastructure.LessonRunRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Práctica de lecciones: ejecuciones, intentos con evaluación en servidor y regla de dominio (RF-EXR, RN-40). */
@Service
public class PracticeService {

    private static final Pattern IDEMPOTENCY_KEY = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final LessonRunRepository runs;
    private final ExerciseAttemptRepository attempts;
    private final LessonProgressRepository progress;
    private final LearnerProfiles learnerProfiles;
    private final Json json;
    private final Clock clock;
    private final MasteryPolicy masteryPolicy = MasteryPolicy.DEFAULT;

    PracticeService(LessonRepository lessons, ExerciseRepository exercises, LessonRunRepository runs,
            ExerciseAttemptRepository attempts, LessonProgressRepository progress, LearnerProfiles learnerProfiles,
            Json json, Clock clock) {
        this.lessons = lessons;
        this.exercises = exercises;
        this.runs = runs;
        this.attempts = attempts;
        this.progress = progress;
        this.learnerProfiles = learnerProfiles;
        this.json = json;
        this.clock = clock;
    }

    public record RunStarted(String runId, String lessonPublicId) {
    }

    public record AttemptCommand(String runId, JsonNode answer, int hintsUsed, Integer durationMs,
            String idempotencyKey) {
    }

    /** Tras responder sí se revela la solución y la explicación (RF-EXR-6). */
    public record AttemptResult(boolean correct, BigDecimal score, JsonNode solution, Localized feedback) {
    }

    public record RunCompleted(BigDecimal score, int correctCount, int totalExercises, String state,
            boolean newlyMastered, int completedRuns) {
    }

    @Transactional
    public RunStarted startRun(Long userId, String lessonPublicId) {
        Lesson lesson = publishedLesson(lessonPublicId);
        Instant now = Instant.now(clock);
        LessonRun run = runs.save(LessonRun.start(userId, lesson.getId(), now));
        progress.insertIfAbsent(userId, lesson.getId(), now);
        return new RunStarted(run.getPublicId(), lesson.getPublicId());
    }

    @Transactional
    public AttemptResult submitAttempt(Long userId, String exercisePublicId, AttemptCommand cmd) {
        if (cmd.idempotencyKey() == null || !IDEMPOTENCY_KEY.matcher(cmd.idempotencyKey()).matches()) {
            throw ApiException.badRequest("INVALID_IDEMPOTENCY_KEY",
                    "Falta una clave de idempotencia válida (8–64 caracteres alfanuméricos).");
        }
        Exercise exercise = exercises.findByPublicId(exercisePublicId)
                .filter(e -> e.getPurpose() == Exercise.Purpose.LESSON && e.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("EXERCISE_NOT_FOUND", "Ejercicio no encontrado."));
        Lesson lesson = lessons.findById(exercise.getLessonId())
                .filter(l -> l.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("EXERCISE_NOT_FOUND", "Ejercicio no encontrado."));

        // Reintento de red con la misma clave: se devuelve el resultado original, no se registra otro intento.
        var existing = attempts.findByUserIdAndIdempotencyKey(userId, cmd.idempotencyKey());
        if (existing.isPresent()) {
            ExerciseAttempt a = existing.get();
            if (!a.getExerciseId().equals(exercise.getId())) {
                throw ApiException.conflict("IDEMPOTENCY_KEY_REUSED", "La clave ya se usó para otro ejercicio.");
            }
            return result(exercise, a.isCorrect(), a.getScore());
        }

        Long runId = null;
        if (cmd.runId() != null) {
            LessonRun run = runs.findByPublicId(cmd.runId())
                    .filter(r -> r.getUserId().equals(userId))
                    .orElseThrow(() -> ApiException.notFound("RUN_NOT_FOUND", "Ejecución de lección no encontrada."));
            if (!run.getLessonId().equals(lesson.getId())) {
                throw ApiException.badRequest("RUN_LESSON_MISMATCH", "El ejercicio no pertenece a esta lección.");
            }
            if (run.isCompleted()) {
                throw ApiException.conflict("RUN_COMPLETED", "Esta lección ya se terminó. Empieza de nuevo para practicar.");
            }
            runId = run.getId();
        }

        ExerciseEvaluator.Evaluation evaluation;
        try {
            evaluation = ExerciseEvaluator.evaluate(exercise.getType(), json.read(exercise.getPromptJson()),
                    json.read(exercise.getSolutionJson()), cmd.answer());
        } catch (InvalidExerciseException ex) {
            throw ApiException.badRequest("INVALID_ANSWER", "La respuesta no tiene el formato esperado: " + ex.getMessage());
        }

        Instant now = Instant.now(clock);
        attempts.save(ExerciseAttempt.record(userId, exercise.getId(), runId, json.write(cmd.answer()), evaluation,
                cmd.hintsUsed(), cmd.durationMs(), cmd.idempotencyKey(), now));
        progress.insertIfAbsent(userId, lesson.getId(), now);
        return result(exercise, evaluation.correct(), evaluation.score());
    }

    @Transactional
    public RunCompleted completeRun(Long userId, String runPublicId) {
        LessonRun run = runs.findByPublicIdForUpdate(runPublicId)
                .filter(r -> r.getUserId().equals(userId))
                .orElseThrow(() -> ApiException.notFound("RUN_NOT_FOUND", "Ejecución de lección no encontrada."));
        LessonProgress.Key key = new LessonProgress.Key(userId, run.getLessonId());

        List<Exercise> lessonExercises = exercises
                .findByLessonIdAndStatusOrderBySortOrderAsc(run.getLessonId(), ContentStatus.PUBLISHED);
        Map<Long, ExerciseAttempt> firstAttempt = new HashMap<>();
        for (ExerciseAttempt a : attempts.findByLessonRunIdOrderByIdAsc(run.getId())) {
            firstAttempt.putIfAbsent(a.getExerciseId(), a);
        }
        int correct = (int) lessonExercises.stream()
                .map(e -> firstAttempt.get(e.getId()))
                .filter(a -> a != null && a.isCorrect())
                .count();

        if (run.isCompleted()) {
            // Idempotente: terminar dos veces devuelve el mismo resultado.
            LessonProgress p = progress.findById(key).orElseThrow();
            return new RunCompleted(run.getScore(), correct, lessonExercises.size(), p.getState().name(), false,
                    p.getCompletedRuns());
        }

        // Puntaje = promedio del PRIMER intento de cada ejercicio (los reintentos ayudan a aprender, no a puntuar).
        BigDecimal sum = lessonExercises.stream()
                .map(e -> firstAttempt.get(e.getId()))
                .map(a -> a == null ? BigDecimal.ZERO : a.getScore())
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal score = lessonExercises.isEmpty() ? BigDecimal.ZERO
                : sum.divide(BigDecimal.valueOf(lessonExercises.size()), 4, RoundingMode.HALF_UP);

        Instant now = Instant.now(clock);
        LocalDate today = LocalDate.ofInstant(now, learnerProfiles.zoneOf(userId));
        run.complete(score, today, now);

        List<MasteryPolicy.RunResult> history = runs
                .findByUserIdAndLessonIdAndCompletedAtIsNotNullOrderByIdAsc(userId, run.getLessonId()).stream()
                .map(r -> new MasteryPolicy.RunResult(r.getLocalDate(), r.getScore(), r.getId()))
                .toList();
        MasteryPolicy.State state = masteryPolicy.evaluate(history);

        progress.insertIfAbsent(userId, run.getLessonId(), now);
        LessonProgress p = progress.findById(key).orElseThrow();
        boolean wasMastered = p.getMasteredAt() != null;
        p.recordCompletedRun(score, state, now);
        return new RunCompleted(score, correct, lessonExercises.size(), state.name(),
                !wasMastered && state == MasteryPolicy.State.MASTERED, p.getCompletedRuns());
    }

    private AttemptResult result(Exercise exercise, boolean correct, BigDecimal score) {
        return new AttemptResult(correct, score, json.read(exercise.getSolutionJson()), exercise.getFeedback());
    }

    private Lesson publishedLesson(String publicId) {
        return lessons.findByPublicId(publicId)
                .filter(l -> l.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "Lección no encontrada."));
    }
}
