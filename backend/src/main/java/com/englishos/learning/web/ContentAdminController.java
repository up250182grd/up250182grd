package com.englishos.learning.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Localized;
import com.englishos.learning.api.Skill;
import com.englishos.learning.application.ContentAdminService;
import com.englishos.learning.application.ContentAdminService.AdminCourseNode;
import com.englishos.learning.application.ContentAdminService.AdminExercise;
import com.englishos.learning.application.ContentAdminService.AdminLesson;
import com.englishos.learning.application.ContentAdminService.Direction;
import com.englishos.learning.application.ContentAdminService.ExerciseInput;
import com.englishos.learning.application.ContentAdminService.LessonInput;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.ExerciseType;
import com.englishos.shared.error.ApiException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Gestión de contenido (solo ADMIN por URL; permisos content:* verificados además en el servicio). */
@RestController
@RequestMapping("/api/v1/admin/content")
class ContentAdminController {

    private final ContentAdminService service;

    ContentAdminController(ContentAdminService service) {
        this.service = service;
    }

    record TextInput(@NotBlank @Size(max = 20000) String es, @NotBlank @Size(max = 20000) String en) {
        Localized toLocalized() {
            return new Localized(es.trim(), en.trim());
        }
    }

    record LessonRequest(
            @Size(max = 80) String slug,
            CefrLevel level,
            @Valid @NotNull TextInput title,
            @Valid @NotNull TextInput objective,
            @Valid @NotNull TextInput explanation,
            @Size(max = 50000) String examplesJson,
            @Size(max = 50000) String vocabularyJson,
            @Min(1) @Max(180) int estimatedMinutes) {

        LessonInput toInput() {
            return new LessonInput(slug, level, title.toLocalized(), objective.toLocalized(),
                    explanation.toLocalized(), examplesJson, vocabularyJson, estimatedMinutes);
        }
    }

    record ExerciseRequest(
            @Size(max = 80) String slug,
            @NotNull ExerciseType type,
            @NotNull Skill skill,
            @NotNull CefrLevel level,
            @Valid @NotNull TextInput instructions,
            @NotBlank @Size(max = 20000) String promptJson,
            @NotBlank @Size(max = 20000) String solutionJson,
            @Valid @NotNull TextInput feedback,
            @Valid TextInput hint) {

        ExerciseInput toInput() {
            return new ExerciseInput(slug, type, skill, level, instructions.toLocalized(), promptJson, solutionJson,
                    feedback.toLocalized(), hint == null ? null : hint.toLocalized());
        }
    }

    record StatusRequest(@NotNull ContentStatus status) {
    }

    record MoveRequest(@NotNull Direction direction) {
    }

    @GetMapping("/tree")
    List<AdminCourseNode> tree() {
        return service.tree();
    }

    @GetMapping("/lessons/{publicId}")
    AdminLesson lesson(@PathVariable String publicId) {
        return service.lesson(publicId);
    }

    @PostMapping("/modules/{modulePublicId}/lessons")
    @ResponseStatus(HttpStatus.CREATED)
    AdminLesson createLesson(@AuthenticationPrincipal CurrentUser actor, @PathVariable String modulePublicId,
            @Valid @RequestBody LessonRequest req) {
        if (req.slug() == null || req.level() == null) {
            throw ApiException.badRequest("SLUG_AND_LEVEL_REQUIRED", "Indica el identificador y el nivel de la lección.");
        }
        return service.createLesson(actor, modulePublicId, req.toInput());
    }

    @PutMapping("/lessons/{publicId}")
    AdminLesson updateLesson(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody LessonRequest req) {
        return service.updateLesson(actor, publicId, req.toInput());
    }

    @PostMapping("/lessons/{publicId}/status")
    AdminLesson lessonStatus(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody StatusRequest req) {
        return service.changeLessonStatus(actor, publicId, req.status());
    }

    @PostMapping("/lessons/{publicId}/review")
    AdminLesson review(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId) {
        return service.markReviewed(actor, publicId);
    }

    @PostMapping("/lessons/{publicId}/move")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void move(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody MoveRequest req) {
        service.moveLesson(actor, publicId, req.direction());
    }

    @PostMapping("/lessons/{publicId}/exercises")
    @ResponseStatus(HttpStatus.CREATED)
    AdminExercise createExercise(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody ExerciseRequest req) {
        return service.createExercise(actor, publicId, req.toInput());
    }

    @PutMapping("/exercises/{publicId}")
    AdminExercise updateExercise(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody ExerciseRequest req) {
        return service.updateExercise(actor, publicId, req.toInput());
    }

    @PostMapping("/exercises/{publicId}/status")
    AdminExercise exerciseStatus(@AuthenticationPrincipal CurrentUser actor, @PathVariable String publicId,
            @Valid @RequestBody StatusRequest req) {
        return service.changeExerciseStatus(actor, publicId, req.status());
    }
}
