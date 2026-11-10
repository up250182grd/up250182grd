package com.englishos.learning.domain;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;

import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import com.englishos.learning.api.Localized;
import com.englishos.learning.api.CefrLevel;
import com.englishos.shared.ids.Ulid;

@Entity
@Table(name = "lessons")
public class Lesson {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "module_id", nullable = false, updatable = false)
    private Long moduleId;

    @Column(nullable = false, updatable = false, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "cefr_level", nullable = false, length = 2)
    private CefrLevel level;

    @Column(name = "title_es", nullable = false)
    private String titleEs;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "objective_es", nullable = false)
    private String objectiveEs;

    @Column(name = "objective_en", nullable = false)
    private String objectiveEn;

    @Column(name = "explanation_es", nullable = false)
    private String explanationEs;

    @Column(name = "explanation_en", nullable = false)
    private String explanationEn;

    /** [{en, es}] — oraciones de ejemplo con traducción. */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String examples;

    /** [{term, translation, partOfSpeech, ipa, example}] */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false)
    private String vocabulary;

    @Column(name = "estimated_minutes", nullable = false)
    private int estimatedMinutes;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "content_version", nullable = false)
    private int contentVersion;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    @ElementCollection(fetch = FetchType.LAZY)
    @CollectionTable(name = "lesson_prerequisites", joinColumns = @JoinColumn(name = "lesson_id"))
    @Column(name = "prerequisite_lesson_id")
    private Set<Long> prerequisiteIds = new HashSet<>();

    protected Lesson() {
    }

    public static Lesson create(Long moduleId, String slug, CefrLevel level, Localized title, Localized objective,
            Localized explanation, String examplesJson, String vocabularyJson, int estimatedMinutes,
            ContentStatus status, int sortOrder, Instant now) {
        Lesson l = new Lesson();
        l.publicId = Ulid.next();
        l.moduleId = moduleId;
        l.slug = slug;
        l.level = level;
        l.status = status;
        l.sortOrder = sortOrder;
        l.contentVersion = 1;
        l.createdAt = now;
        l.applyTexts(title, objective, explanation, examplesJson, vocabularyJson, estimatedMinutes);
        l.updatedAt = now;
        return l;
    }

    /** Edición del contenido: incrementa la versión de contenido y marca la lección como pendiente de revisión. */
    public void edit(Localized title, Localized objective, Localized explanation, String examplesJson,
            String vocabularyJson, int estimatedMinutes, Instant now) {
        applyTexts(title, objective, explanation, examplesJson, vocabularyJson, estimatedMinutes);
        contentVersion++;
        reviewedAt = null;
        updatedAt = now;
    }

    private void applyTexts(Localized title, Localized objective, Localized explanation, String examplesJson,
            String vocabularyJson, int minutes) {
        if (minutes < 1 || minutes > 180) {
            throw new IllegalArgumentException("Duración estimada fuera de rango");
        }
        titleEs = title.es();
        titleEn = title.en();
        objectiveEs = objective.es();
        objectiveEn = objective.en();
        explanationEs = explanation.es();
        explanationEn = explanation.en();
        examples = examplesJson;
        vocabulary = vocabularyJson;
        estimatedMinutes = minutes;
    }

    public void changeStatus(ContentStatus newStatus, Instant now) {
        status = newStatus;
        updatedAt = now;
    }

    public void markReviewed(Instant now) {
        reviewedAt = now;
        updatedAt = now;
    }

    public void moveTo(int newSortOrder, Instant now) {
        sortOrder = newSortOrder;
        updatedAt = now;
    }

    public void setPrerequisites(Set<Long> ids) {
        prerequisiteIds.clear();
        prerequisiteIds.addAll(ids);
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId;
    }

    public Long getModuleId() {
        return moduleId;
    }

    public String getSlug() {
        return slug;
    }

    public CefrLevel getLevel() {
        return level;
    }

    public Localized getTitle() {
        return new Localized(titleEs, titleEn);
    }

    public Localized getObjective() {
        return new Localized(objectiveEs, objectiveEn);
    }

    public Localized getExplanation() {
        return new Localized(explanationEs, explanationEn);
    }

    public String getExamplesJson() {
        return examples;
    }

    public String getVocabularyJson() {
        return vocabulary;
    }

    public int getEstimatedMinutes() {
        return estimatedMinutes;
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public int getContentVersion() {
        return contentVersion;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }

    public Set<Long> getPrerequisiteIds() {
        return Set.copyOf(prerequisiteIds);
    }
}
