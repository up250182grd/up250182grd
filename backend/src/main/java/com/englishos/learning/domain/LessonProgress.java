package com.englishos.learning.domain;

import java.io.Serial;
import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/** Estado de dominio de una lección para un alumno. Se recalcula al terminar cada ejecución. */
@Entity
@Table(name = "lesson_progress")
public class LessonProgress {

    @Embeddable
    public static class Key implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "user_id")
        private Long userId;

        @Column(name = "lesson_id")
        private Long lessonId;

        protected Key() {
        }

        public Key(Long userId, Long lessonId) {
            this.userId = userId;
            this.lessonId = lessonId;
        }

        public Long getLessonId() {
            return lessonId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && Objects.equals(lessonId, k.lessonId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, lessonId);
        }
    }

    @EmbeddedId
    private Key key;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private MasteryPolicy.State state;

    @Column(name = "best_score", precision = 5, scale = 4)
    private BigDecimal bestScore;

    @Column(name = "last_score", precision = 5, scale = 4)
    private BigDecimal lastScore;

    @Column(name = "completed_runs", nullable = false)
    private int completedRuns;

    @Column(name = "last_activity_at", nullable = false)
    private Instant lastActivityAt;

    @Column(name = "mastered_at")
    private Instant masteredAt;

    @Version
    private int version;

    protected LessonProgress() {
    }

    public static LessonProgress start(Long userId, Long lessonId, Instant now) {
        LessonProgress p = new LessonProgress();
        p.key = new Key(userId, lessonId);
        p.state = MasteryPolicy.State.IN_PROGRESS;
        p.lastActivityAt = now;
        return p;
    }

    public void recordCompletedRun(BigDecimal score, MasteryPolicy.State newState, Instant now) {
        completedRuns++;
        lastScore = score;
        if (bestScore == null || score.compareTo(bestScore) > 0) {
            bestScore = score;
        }
        if (newState == MasteryPolicy.State.MASTERED && masteredAt == null) {
            masteredAt = now;
        }
        state = newState;
        lastActivityAt = now;
    }

    public void touch(Instant now) {
        lastActivityAt = now;
    }

    public Long getLessonId() {
        return key.getLessonId();
    }

    public MasteryPolicy.State getState() {
        return state;
    }

    public BigDecimal getBestScore() {
        return bestScore;
    }

    public BigDecimal getLastScore() {
        return lastScore;
    }

    public int getCompletedRuns() {
        return completedRuns;
    }

    public Instant getMasteredAt() {
        return masteredAt;
    }
}
