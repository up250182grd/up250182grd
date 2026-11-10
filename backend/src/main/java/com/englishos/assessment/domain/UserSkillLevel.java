package com.englishos.assessment.domain;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;

/** Nivel estimado actual del alumno por habilidad. */
@Entity
@Table(name = "user_skill_levels")
public class UserSkillLevel {

    @Embeddable
    public static class Key implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Column(name = "user_id")
        private Long userId;

        @Enumerated(EnumType.STRING)
        @Column(name = "skill", length = 16)
        private Skill skill;

        protected Key() {
        }

        public Key(Long userId, Skill skill) {
            this.userId = userId;
            this.skill = skill;
        }

        public Skill getSkill() {
            return skill;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key k && Objects.equals(userId, k.userId) && skill == k.skill;
        }

        @Override
        public int hashCode() {
            return Objects.hash(userId, skill);
        }
    }

    @EmbeddedId
    private Key key;

    @Enumerated(EnumType.STRING)
    @Column(name = "estimated_level", nullable = false, length = 2)
    private CefrLevel level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Staircase.Confidence confidence;

    @Column(name = "evidence_count", nullable = false)
    private int evidenceCount;

    @Column(nullable = false, length = 16)
    private String source;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected UserSkillLevel() {
    }

    public static UserSkillLevel of(Long userId, Staircase.Estimate e, String source, Instant now) {
        UserSkillLevel l = new UserSkillLevel();
        l.key = new Key(userId, e.skill());
        l.update(e, source, now);
        return l;
    }

    public void update(Staircase.Estimate e, String source, Instant now) {
        level = e.level();
        confidence = e.confidence();
        evidenceCount = e.evidence();
        this.source = source;
        updatedAt = now;
    }

    public Skill getSkill() {
        return key.getSkill();
    }

    public CefrLevel getLevel() {
        return level;
    }

    public Staircase.Confidence getConfidence() {
        return confidence;
    }

    public int getEvidenceCount() {
        return evidenceCount;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
