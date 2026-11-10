package com.englishos.learning.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englishos.learning.api.CefrLevel;

/** Plan de estudio: secuencia ordenada de cursos a partir del nivel estimado del alumno. */
@Entity
@Table(name = "learning_plans")
public class LearningPlan {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(nullable = false, updatable = false, length = 16)
    private String source;

    @Enumerated(EnumType.STRING)
    @Column(name = "start_level", nullable = false, updatable = false, length = 2)
    private CefrLevel startLevel;

    /** Lista JSON de IDs internos de curso, en orden. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "course_ids", nullable = false, updatable = false)
    private String courseIds;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LearningPlan() {
    }

    public static LearningPlan create(Long userId, String source, CefrLevel startLevel, String courseIdsJson,
            Instant now) {
        LearningPlan p = new LearningPlan();
        p.userId = userId;
        p.source = source;
        p.startLevel = startLevel;
        p.courseIds = courseIdsJson;
        p.active = true;
        p.createdAt = now;
        return p;
    }

    public void deactivate() {
        active = false;
    }

    public CefrLevel getStartLevel() {
        return startLevel;
    }

    public String getCourseIdsJson() {
        return courseIds;
    }

    public String getSource() {
        return source;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
