package com.englishos.learning.domain;

import java.math.BigDecimal;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Intento inmutable de un ejercicio (RF-EXR-5). */
@Entity
@Table(name = "exercise_attempts")
public class ExerciseAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "exercise_id", nullable = false, updatable = false)
    private Long exerciseId;

    @Column(name = "lesson_run_id", updatable = false)
    private Long lessonRunId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, updatable = false)
    private String answer;

    @Column(name = "is_correct", nullable = false, updatable = false)
    private boolean correct;

    @Column(nullable = false, updatable = false, precision = 5, scale = 4)
    private BigDecimal score;

    @Column(nullable = false, updatable = false, length = 16)
    private String evaluation;

    @Column(name = "hints_used", nullable = false, updatable = false)
    private int hintsUsed;

    @Column(name = "duration_ms", updatable = false)
    private Integer durationMs;

    @Column(name = "idempotency_key", nullable = false, updatable = false, length = 64)
    private String idempotencyKey;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected ExerciseAttempt() {
    }

    public static ExerciseAttempt record(Long userId, Long exerciseId, Long lessonRunId, String answerJson,
            ExerciseEvaluator.Evaluation result, int hintsUsed, Integer durationMs, String idempotencyKey,
            Instant now) {
        ExerciseAttempt a = new ExerciseAttempt();
        a.userId = userId;
        a.exerciseId = exerciseId;
        a.lessonRunId = lessonRunId;
        a.answer = answerJson;
        a.correct = result.correct();
        a.score = result.score();
        a.evaluation = "RULE";
        a.hintsUsed = Math.max(0, hintsUsed);
        a.durationMs = durationMs;
        a.idempotencyKey = idempotencyKey;
        a.createdAt = now;
        return a;
    }

    public Long getId() {
        return id;
    }

    public Long getExerciseId() {
        return exerciseId;
    }

    public Long getLessonRunId() {
        return lessonRunId;
    }

    public boolean isCorrect() {
        return correct;
    }

    public BigDecimal getScore() {
        return score;
    }
}
