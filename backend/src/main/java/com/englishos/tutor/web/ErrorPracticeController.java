package com.englishos.tutor.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.tutor.application.ErrorPracticeService;
import com.englishos.tutor.application.ErrorPracticeService.ErrorAnswerResult;
import com.englishos.tutor.application.ErrorPracticeService.ErrorItem;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Práctica de los errores que el profesor corrigió (sin IA y sin costo). */
@RestController
@RequestMapping("/api/v1/practice/errors")
class ErrorPracticeController {

    private final ErrorPracticeService practice;

    ErrorPracticeController(ErrorPracticeService practice) {
        this.practice = practice;
    }

    record AnswerRequest(@NotNull @Size(max = 500) String answer) {
    }

    @GetMapping
    List<ErrorItem> queue(@AuthenticationPrincipal CurrentUser user) {
        return practice.queue(user.id());
    }

    @PostMapping("/{id}/answer")
    ErrorAnswerResult answer(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @Valid @RequestBody AnswerRequest req) {
        return practice.answer(user.id(), id, req.answer());
    }
}
