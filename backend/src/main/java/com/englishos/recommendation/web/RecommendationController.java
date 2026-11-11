package com.englishos.recommendation.web;

import java.util.List;
import java.util.Map;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.recommendation.application.RecommendationService;
import com.englishos.recommendation.application.RecommendationService.LearnerProgressView;
import com.englishos.recommendation.application.RecommendationService.RecommendationsView;
import com.englishos.recommendation.application.RecommendationService.RecommendationWeightsView;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
class RecommendationController {

    private final RecommendationService service;

    RecommendationController(RecommendationService service) {
        this.service = service;
    }

    record WeightsRequest(@NotNull Map<String, Integer> weights, @NotBlank @Size(max = 500) String note) {
    }

    @GetMapping("/api/v1/me/recommendations")
    RecommendationsView recommendations(@AuthenticationPrincipal CurrentUser user) {
        return service.recommendations(user);
    }

    @GetMapping("/api/v1/me/progress")
    LearnerProgressView progress(@AuthenticationPrincipal CurrentUser user) {
        return service.progress(user);
    }

    @GetMapping("/api/v1/admin/recommendations/weights")
    List<RecommendationWeightsView> weights() {
        return service.weightsHistory();
    }

    @PostMapping("/api/v1/admin/recommendations/weights")
    @ResponseStatus(HttpStatus.CREATED)
    RecommendationWeightsView updateWeights(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody WeightsRequest req) {
        return service.updateWeights(user, req.weights(), req.note());
    }
}
