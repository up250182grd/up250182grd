package com.englishos.identity.application;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;

import com.englishos.identity.api.LearnerProfiles;
import com.englishos.identity.domain.UserProfile;
import com.englishos.identity.infrastructure.UserProfileRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
class LearnerProfilesService implements LearnerProfiles {

    private static final ZoneId DEFAULT_ZONE = ZoneId.of("America/Mexico_City");

    private final UserProfileRepository profiles;
    private final com.englishos.identity.infrastructure.UserRepository users;
    private final Clock clock;

    LearnerProfilesService(UserProfileRepository profiles, com.englishos.identity.infrastructure.UserRepository users,
            Clock clock) {
        this.profiles = profiles;
        this.users = users;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public ZoneId zoneOf(Long userId) {
        return profiles.findById(userId).map(UserProfile::getTimezone).map(tz -> {
            try {
                return ZoneId.of(tz);
            } catch (DateTimeException ex) {
                return DEFAULT_ZONE;
            }
        }).orElse(DEFAULT_ZONE);
    }

    @Override
    @Transactional
    public void markOnboardingCompleted(Long userId) {
        profiles.findById(userId).ifPresent(p -> p.completeOnboarding(Instant.now(clock)));
    }

    @Override
    @Transactional(readOnly = true)
    public boolean isEmailVerified(Long userId) {
        return users.findById(userId).map(com.englishos.identity.domain.User::isEmailVerified).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public boolean storeTranscripts(Long userId) {
        return profiles.findById(userId).map(UserProfile::isStoreTranscripts).orElse(false);
    }

    @Override
    @Transactional(readOnly = true)
    public String explanationLanguage(Long userId) {
        return profiles.findById(userId).map(UserProfile::getExplanationLanguage).orElse("es");
    }
}
