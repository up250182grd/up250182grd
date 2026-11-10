package com.englishos.assessment.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import tools.jackson.databind.JsonNode;

import com.englishos.assessment.application.AssessmentService;
import com.englishos.assessment.application.AssessmentService.NextQuestion;
import com.englishos.assessment.application.AssessmentService.Result;
import com.englishos.assessment.application.AssessmentService.SessionView;
import com.englishos.assessment.application.AssessmentService.SkillLevelView;
import com.englishos.identity.api.CurrentUser;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
class AssessmentController {

    private final AssessmentService service;

    AssessmentController(AssessmentService service) {
        this.service = service;
    }

    record ResponseRequest(@NotBlank @Size(max = 26) String itemId, @NotNull JsonNode answer) {
    }

    @PostMapping("/assessments")
    SessionView start(@AuthenticationPrincipal CurrentUser user) {
        return service.start(user.id());
    }

    @PostMapping("/assessments/{sessionId}/next")
    NextQuestion next(@AuthenticationPrincipal CurrentUser user, @PathVariable String sessionId) {
        return service.next(user.id(), sessionId);
    }

    @PostMapping("/assessments/{sessionId}/responses")
    SessionView respond(@AuthenticationPrincipal CurrentUser user, @PathVariable String sessionId,
            @Valid @RequestBody ResponseRequest req) {
        return service.respond(user.id(), sessionId, req.itemId(), req.answer());
    }

    @GetMapping("/assessments/{sessionId}/result")
    Result result(@AuthenticationPrincipal CurrentUser user, @PathVariable String sessionId) {
        return service.result(user.id(), sessionId);
    }

    @GetMapping("/me/skills")
    List<SkillLevelView> skills(@AuthenticationPrincipal CurrentUser user) {
        return service.skillLevels(user.id());
    }
}
