package com.englishos.learning.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import com.englishos.shared.ids.Ulid;

/** Una ejecución de lección: de "empezar" a "terminar". Su puntaje es la base de la regla de dominio. */
@Entity
@Table(name = "lesson_runs")
public class LessonRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "lesson_id", nullable = false, updatable = false)
    private Long lessonId;

    @Column(name = "started_at", nullable = false, updatable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "local_date")
    private LocalDate localDate;

    @Column(precision = 5, scale = 4)
    private BigDecimal score;

    protected LessonRun() {
    }

    public static LessonRun start(Long userId, Long lessonId, Instant now) {
        LessonRun r = new LessonRun();
        r.publicId = Ulid.next();
        r.userId = userId;
        r.lessonId = lessonId;
        r.startedAt = now;
        return r;
    }

    /** @param localDate día en la zona horaria del alumno (los "días distintos" del dominio se cuentan así). */
    public void complete(BigDecimal finalScore, LocalDate localDate, Instant now) {
        if (completedAt != null) {
            throw new IllegalStateException("La ejecución ya terminó");
        }
        this.score = finalScore;
        this.localDate = localDate;
        this.completedAt = now;
    }

    public boolean isCompleted() {
        return completedAt != null;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getUserId() {
        return userId;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public BigDecimal getScore() {
        return score;
    }

    public LocalDate getLocalDate() {
        return localDate;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
