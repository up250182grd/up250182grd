package com.englishos.vocabulary.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.vocabulary.application.VocabularyService;
import com.englishos.vocabulary.application.VocabularyService.VocabularyCardView;
import com.englishos.vocabulary.application.VocabularyService.VocabularyQueue;
import com.englishos.vocabulary.application.VocabularyService.VocabularyReviewResult;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Repaso espaciado: cola del día, calificación de tarjetas y "Mis palabras". */
@RestController
@RequestMapping("/api/v1/vocabulary")
class VocabularyController {

    private final VocabularyService vocabulary;

    VocabularyController(VocabularyService vocabulary) {
        this.vocabulary = vocabulary;
    }

    record ReviewRequest(@Min(0) @Max(5) int quality, @NotBlank @Size(max = 64) String reviewId,
            @Min(0) Integer durationMs) {
    }

    @GetMapping("/queue")
    VocabularyQueue queue(@AuthenticationPrincipal CurrentUser user) {
        return vocabulary.queue(user.id());
    }

    @PostMapping("/cards/{id}/reviews")
    VocabularyReviewResult review(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @Valid @RequestBody ReviewRequest req) {
        return vocabulary.review(user.id(), id, req.quality(), req.reviewId(), req.durationMs());
    }

    @GetMapping("/cards")
    List<VocabularyCardView> words(@AuthenticationPrincipal CurrentUser user) {
        return vocabulary.words(user.id());
    }
}
