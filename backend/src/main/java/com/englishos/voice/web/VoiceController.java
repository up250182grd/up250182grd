package com.englishos.voice.web;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.identity.api.CurrentUser;
import com.englishos.voice.application.VoiceService;
import com.englishos.voice.application.VoiceService.VoiceQuote;
import com.englishos.voice.application.VoiceService.VoiceSessionSummary;
import com.englishos.voice.application.VoiceService.VoiceSessionView;
import com.englishos.voice.application.VoiceService.VoiceStartResult;
import com.englishos.voice.domain.VoiceSession;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * API de llamadas de voz. El navegador envía su oferta WebRTC (SDP) y recibe la respuesta del proveedor; nunca recibe
 * la clave del proveedor. {@code /events} emite por SSE: {@code meter}, {@code transcript}, {@code warning},
 * {@code ending} y {@code ended}.
 */
@RestController
@RequestMapping("/api/v1/voice")
class VoiceController {

    private final VoiceService voice;

    VoiceController(VoiceService voice) {
        this.voice = voice;
    }

    record StartRequest(@NotNull VoiceSession.Mode mode, @Size(max = 200) String topic,
            @Size(max = 20000) String sdpOffer) {
    }

    @GetMapping("/quote")
    VoiceQuote quote(@AuthenticationPrincipal CurrentUser user) {
        return voice.quote(user);
    }

    @PostMapping("/sessions")
    @ResponseStatus(HttpStatus.CREATED)
    VoiceStartResult start(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StartRequest req) {
        return voice.start(user, req.mode(), req.topic(), req.sdpOffer());
    }

    @GetMapping("/sessions")
    List<VoiceSessionSummary> list(@AuthenticationPrincipal CurrentUser user) {
        return voice.list(user);
    }

    @GetMapping("/sessions/{id}")
    VoiceSessionView get(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return voice.view(user, id);
    }

    @PostMapping("/sessions/{id}/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        voice.heartbeat(user, id);
    }

    @PostMapping("/sessions/{id}/end")
    VoiceSessionView end(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return voice.end(user, id);
    }

    @GetMapping(path = "/sessions/{id}/events", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter events(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return voice.subscribe(user, id);
    }
}
