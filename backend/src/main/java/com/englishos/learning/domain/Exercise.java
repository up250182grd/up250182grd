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
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englishos.learning.api.Localized;
import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;
import com.englishos.shared.ids.Ulid;

/**
 * Ejercicio de lección o ítem del banco del diagnóstico. La solución vive en el servidor y solo se revela tras
 * responder (RF-EXR-3).
 */
@Entity
@Table(name = "exercises")
public class Exercise {

    public enum Purpose { LESSON, DIAGNOSTIC }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(nullable = false, updatable = false, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 16)
    private Purpose purpose;

    @Column(name = "lesson_id", updatable = false)
    private Long lessonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ExerciseType type;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Skill skill;

    @Enumerated(EnumType.STRING)
    @Column(name = "cefr_level", nullable = false, length = 2)
    private CefrLevel level;

    @Column(name = "instructions_es", nullable = false, length = 500)
    private String instructionsEs;

    @Column(name = "instructions_en", nullable = false, length = 500)
    private String instructionsEn;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String prompt;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String solution;

    @Column(name = "feedback_es", nullable = false)
    private String feedbackEs;

    @Column(name = "feedback_en", nullable = false)
    private String feedbackEn;

    @Column(name = "hint_es", length = 500)
    private String hintEs;

    @Column(name = "hint_en", length = 500)
    private String hintEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected Exercise() {
    }

    public record Definition(ExerciseType type, Skill skill, CefrLevel level, Localized instructions,
            String promptJson, String solutionJson, Localized feedback, Localized hint) {
    }

    public static Exercise create(String slug, Purpose purpose, Long lessonId, Definition def, ContentStatus status,
            int sortOrder, Instant now) {
        if ((purpose == Purpose.LESSON) != (lessonId != null)) {
            throw new IllegalArgumentException("Un ejercicio de lección necesita lección; uno de diagnóstico, no");
        }
        Exercise e = new Exercise();
        e.publicId = Ulid.next();
        e.slug = slug;
        e.purpose = purpose;
        e.lessonId = lessonId;
        e.status = status;
        e.sortOrder = sortOrder;
        e.createdAt = now;
        e.apply(def, now);
        return e;
    }

    public void apply(Definition def, Instant now) {
        type = def.type();
        skill = def.skill();
        level = def.level();
        instructionsEs = def.instructions().es();
        instructionsEn = def.instructions().en();
        prompt = def.promptJson();
        solution = def.solutionJson();
        feedbackEs = def.feedback().es();
        feedbackEn = def.feedback().en();
        hintEs = def.hint() == null ? null : def.hint().es();
        hintEn = def.hint() == null ? null : def.hint().en();
        updatedAt = now;
    }

    public void changeStatus(ContentStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public String getSlug() {
        return slug;
    }

    public Purpose getPurpose() {
        return purpose;
    }

    public Long getLessonId() {
        return lessonId;
    }

    public ExerciseType getType() {
        return type;
    }

    public Skill getSkill() {
        return skill;
    }

    public CefrLevel getLevel() {
        return level;
    }

    public Localized getInstructions() {
        return new Localized(instructionsEs, instructionsEn);
    }

    public String getPromptJson() {
        return prompt;
    }

    public String getSolutionJson() {
        return solution;
    }

    public Localized getFeedback() {
        return new Localized(feedbackEs, feedbackEn);
    }

    public Localized getHint() {
        return Localized.ofNullable(hintEs, hintEn);
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
