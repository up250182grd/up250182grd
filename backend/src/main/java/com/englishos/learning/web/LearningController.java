package com.englishos.learning.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

import com.englishos.identity.api.CurrentUser;
import com.englishos.learning.application.CatalogService;
import com.englishos.learning.application.CatalogService.CourseView;
import com.englishos.learning.application.CatalogService.LearningSummary;
import com.englishos.learning.application.CatalogService.LessonView;
import com.englishos.learning.application.PracticeService;
import com.englishos.learning.application.PracticeService.AttemptCommand;
import com.englishos.learning.application.PracticeService.AttemptResult;
import com.englishos.learning.application.PracticeService.RunCompleted;
import com.englishos.learning.application.PracticeService.RunStarted;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class LearningController {

    private final CatalogService catalog;
    private final PracticeService practice;

    LearningController(CatalogService catalog, PracticeService practice) {
        this.catalog = catalog;
        this.practice = practice;
    }

    record AttemptRequest(
            @Size(max = 26) String runId,
            @NotNull JsonNode answer,
            @Min(0) @Max(20) int hintsUsed,
            @Min(0) @Max(3_600_000) Integer durationMs,
            @NotBlank @Size(min = 8, max = 64) String idempotencyKey) {
    }

    @GetMapping("/courses")
    List<CourseView> courses(@AuthenticationPrincipal CurrentUser user) {
        return catalog.courseTree(user.id());
    }

    @GetMapping("/lessons/{publicId}")
    LessonView lesson(@AuthenticationPrincipal CurrentUser user, @PathVariable String publicId) {
        return catalog.lessonDetail(user.id(), publicId);
    }

    @GetMapping("/me/learning-summary")
    LearningSummary summary(@AuthenticationPrincipal CurrentUser user) {
        return catalog.summary(user.id());
    }

    @PostMapping("/lessons/{publicId}/runs")
    @ResponseStatus(HttpStatus.CREATED)
    RunStarted startRun(@AuthenticationPrincipal CurrentUser user, @PathVariable String publicId) {
        return practice.startRun(user.id(), publicId);
    }

    @PostMapping("/exercises/{publicId}/attempts")
    AttemptResult attempt(@AuthenticationPrincipal CurrentUser user, @PathVariable String publicId,
            @Valid @RequestBody AttemptRequest req) {
        return practice.submitAttempt(user.id(), publicId,
                new AttemptCommand(req.runId(), req.answer(), req.hintsUsed(), req.durationMs(), req.idempotencyKey()));
    }

    @PostMapping("/lesson-runs/{runId}/complete")
    RunCompleted complete(@AuthenticationPrincipal CurrentUser user, @PathVariable String runId) {
        return practice.completeRun(user.id(), runId);
    }
}
