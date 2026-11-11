package com.englishos.gamification.web;

import com.englishos.gamification.application.GamificationService;
import com.englishos.gamification.application.GamificationService.GamificationView;
import com.englishos.identity.api.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class GamificationController {

    private final GamificationService game;

    GamificationController(GamificationService game) {
        this.game = game;
    }

    @GetMapping("/api/v1/me/achievements")
    GamificationView achievements(@AuthenticationPrincipal CurrentUser user) {
        return game.view(user.id());
    }
}
