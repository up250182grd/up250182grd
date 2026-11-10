package com.englishos.identity.application;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.domain.User;
import com.englishos.identity.domain.UserProfile;
import com.englishos.identity.infrastructure.UserProfileRepository;
import com.englishos.identity.infrastructure.UserRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Consulta y edición del perfil propio (RF-IDN-6). */
@Service
public class ProfileService {

    private final UserRepository users;
    private final UserProfileRepository profiles;
    private final Clock clock;

    ProfileService(UserRepository users, UserProfileRepository profiles, Clock clock) {
        this.users = users;
        this.profiles = profiles;
        this.clock = clock;
    }

    public record MeView(String publicId, String email, boolean emailVerified, Set<String> roles,
            Set<String> permissions, ProfileView profile) {
    }

    public record ProfileView(String displayName, String explanationLanguage, String timezone, int dailyGoalMinutes,
            boolean storeTranscripts, boolean onboardingCompleted) {
    }

    public record UpdateProfileCommand(String displayName, String explanationLanguage, String timezone,
            Integer dailyGoalMinutes, Boolean storeTranscripts) {
    }

    @Transactional(readOnly = true)
    public MeView me(CurrentUser current) {
        User user = users.findById(current.id())
                .orElseThrow(() -> ApiException.unauthorized("SESSION_INVALID", "Tu sesión ya no es válida."));
        UserProfile profile = profiles.findById(user.getId()).orElseThrow();
        return new MeView(user.getPublicId(), user.getEmail(), user.isEmailVerified(), user.roleCodes(),
                user.permissionCodes(), toView(profile));
    }

    @Transactional
    public ProfileView update(CurrentUser current, UpdateProfileCommand cmd) {
        UserProfile profile = profiles.findById(current.id()).orElseThrow();
        String displayName = cmd.displayName() == null ? null : cmd.displayName().trim();
        profile.update(displayName, cmd.explanationLanguage(), cmd.timezone(), cmd.dailyGoalMinutes(),
                cmd.storeTranscripts(), Instant.now(clock));
        return toView(profile);
    }

    private static ProfileView toView(UserProfile p) {
        return new ProfileView(p.getDisplayName(), p.getExplanationLanguage(), p.getTimezone(),
                p.getDailyGoalMinutes(), p.isStoreTranscripts(), p.isOnboardingCompleted());
    }
}
