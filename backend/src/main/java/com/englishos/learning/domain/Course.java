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
import com.englishos.learning.api.CefrLevel;
import com.englishos.shared.ids.Ulid;

@Entity
@Table(name = "courses")
public class Course {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "public_id", nullable = false, updatable = false, length = 26)
    private String publicId;

    @Column(nullable = false, updatable = false, length = 80)
    private String slug;

    @Enumerated(EnumType.STRING)
    @Column(name = "cefr_level", nullable = false, length = 2)
    private CefrLevel level;

    @Column(name = "title_es", nullable = false)
    private String titleEs;

    @Column(name = "title_en", nullable = false)
    private String titleEn;

    @Column(name = "description_es", nullable = false)
    private String descriptionEs;

    @Column(name = "description_en", nullable = false)
    private String descriptionEn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private ContentStatus status;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "reviewed_at")
    private Instant reviewedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected Course() {
    }

    public static Course create(String slug, CefrLevel level, Localized title, Localized description,
            ContentStatus status, int sortOrder, Instant now) {
        Course c = new Course();
        c.publicId = Ulid.next();
        c.slug = slug;
        c.level = level;
        c.titleEs = title.es();
        c.titleEn = title.en();
        c.descriptionEs = description.es();
        c.descriptionEn = description.en();
        c.status = status;
        c.sortOrder = sortOrder;
        c.createdAt = now;
        c.updatedAt = now;
        return c;
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

    public CefrLevel getLevel() {
        return level;
    }

    public Localized getTitle() {
        return new Localized(titleEs, titleEn);
    }

    public Localized getDescription() {
        return new Localized(descriptionEs, descriptionEn);
    }

    public ContentStatus getStatus() {
        return status;
    }

    public int getSortOrder() {
        return sortOrder;
    }

    public Instant getReviewedAt() {
        return reviewedAt;
    }
}
