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

import com.englishos.learning.api.Localized;
import com.englishos.shared.ids.Ulid;

@Entity
@Table(name = "course_modules")
public class CourseModule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(name = "course_id", nullable = false, updatable = false)
    private Long courseId;

    @Column(nullable = false, updatable = false, length = 80)
    private String slug;

    @Column(name = "title_es", nullable = false)
    private String titleEs;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "objective_es", nullable = false)
    private String objectiveEs;

    @Column(name = "objective_en", nullable = false)
    private String objectiveEn;

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

    protected CourseModule() {
    }

    public static CourseModule create(Long courseId, String slug, Localized title, Localized objective,
            ContentStatus status, int sortOrder, Instant now) {
        CourseModule m = new CourseModule();
        m.publicId = Ulid.next();
        m.courseId = courseId;
        m.slug = slug;
        m.titleEs = title.es();
        m.titleEn = title.en();
        m.objectiveEs = objective.es();
        m.objectiveEn = objective.en();
        m.status = status;
        m.sortOrder = sortOrder;
        m.createdAt = now;
        m.updatedAt = now;
        return m;
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

    public Long getCourseId() {
        return courseId;
    }

    public String getSlug() {
        return slug;
    }

    public Localized getTitle() {
        return new Localized(titleEs, titleEn);
    }

    public Localized getObjective() {
        return new Localized(objectiveEs, objectiveEn);
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }
}
