package com.englishos.identity.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "user_profiles")
public class UserProfile {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "display_name", nullable = false, length = 80)
    private String displayName;

    @Column(name = "explanation_language", nullable = false, length = 8)
    private String explanationLanguage;

    @Column(nullable = false, length = 64)
    private String timezone;

    @Column(name = "daily_goal_minutes", nullable = false)
    private int dailyGoalMinutes;

    @Column(name = "store_transcripts", nullable = false)
    private boolean storeTranscripts;

    @Column(name = "onboarding_completed", nullable = false)
    private boolean onboardingCompleted;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private int version;

    protected UserProfile() {
    }

    public static UserProfile createDefault(Long userId, String displayName, Instant now) {
        UserProfile p = new UserProfile();
        p.userId = userId;
        p.displayName = displayName;
        p.explanationLanguage = "es";
        p.timezone = "America/Mexico_City";
        p.dailyGoalMinutes = 20;
        p.storeTranscripts = true;
        p.onboardingCompleted = false;
        p.createdAt = now;
        p.updatedAt = now;
        return p;
    }

    public void update(String displayName, String explanationLanguage, String timezone, Integer dailyGoalMinutes,
            Boolean storeTranscripts, Instant now) {
        if (displayName != null) {
            this.displayName = displayName;
        }
        if (explanationLanguage != null) {
            this.explanationLanguage = explanationLanguage;
        }
        if (timezone != null) {
            this.timezone = timezone;
        }
        if (dailyGoalMinutes != null) {
            this.dailyGoalMinutes = dailyGoalMinutes;
        }
        if (storeTranscripts != null) {
            this.storeTranscripts = storeTranscripts;
        }
        this.updatedAt = now;
    }

    public Long getUserId() {
        return userId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getExplanationLanguage() {
        return explanationLanguage;
    }

    public String getTimezone() {
        return timezone;
    }

    public int getDailyGoalMinutes() {
        return dailyGoalMinutes;
    }

    public boolean isStoreTranscripts() {
        return storeTranscripts;
    }

    public boolean isOnboardingCompleted() {
        return onboardingCompleted;
    }
}
