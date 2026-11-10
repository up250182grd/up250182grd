package com.englishos.identity.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.application.ProfileService;
import com.englishos.identity.application.ProfileService.MeView;
import com.englishos.identity.application.ProfileService.ProfileView;
import com.englishos.identity.application.ProfileService.UpdateProfileCommand;
import com.englishos.shared.error.ApiException;

import java.time.DateTimeException;
import java.time.ZoneId;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final ProfileService profiles;

    MeController(ProfileService profiles) {
        this.profiles = profiles;
    }

    record UpdateProfileRequest(
            @Size(min = 1, max = 80) String displayName,
            @Pattern(regexp = "es|en", message = "Idioma no soportado") String explanationLanguage,
            @Size(max = 64) String timezone,
            @Min(5) @Max(240) Integer dailyGoalMinutes,
            Boolean storeTranscripts) {
    }

    @GetMapping
    MeView me(@AuthenticationPrincipal CurrentUser user) {
        return profiles.me(user);
    }

    @PatchMapping("/profile")
    ProfileView updateProfile(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody UpdateProfileRequest req) {
        if (req.timezone() != null) {
            try {
                ZoneId.of(req.timezone());
            } catch (DateTimeException ex) {
                throw ApiException.badRequest("INVALID_TIMEZONE", "Zona horaria no válida.");
            }
        }
        return profiles.update(user, new UpdateProfileCommand(req.displayName(), req.explanationLanguage(),
                req.timezone(), req.dailyGoalMinutes(), req.storeTranscripts()));
    }
}
