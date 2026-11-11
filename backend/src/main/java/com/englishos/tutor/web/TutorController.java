package com.englishos.tutor.web;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import jakarta.annotation.PreDestroy;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.englishos.ai.api.LlmClient.LlmException;
import com.englishos.identity.api.CurrentUser;
import com.englishos.shared.error.ApiException;
import com.englishos.tutor.application.TutorService;
import com.englishos.tutor.application.TutorService.ConversationSummary;
import com.englishos.tutor.application.TutorService.ConversationView;
import com.englishos.tutor.application.TutorService.PreparedTurn;
import com.englishos.tutor.application.TutorService.TurnResult;
import com.englishos.tutor.domain.Conversation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
 * API del profesor por texto. El envío de mensajes responde con Server-Sent Events:
 * {@code delta} (fragmento de texto), {@code done} (correcciones y costo medido) o {@code error}.
 * La validación y la reserva de saldo ocurren ANTES de abrir el flujo, así los errores llegan como HTTP normal.
 */
@RestController
@RequestMapping("/api/v1/tutor/conversations")
class TutorController {

    private static final Logger log = LoggerFactory.getLogger(TutorController.class);

    private final TutorService tutor;
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

    TutorController(TutorService tutor) {
        this.tutor = tutor;
    }

    @PreDestroy
    void shutdown() {
        executor.shutdown();
    }

    record StartRequest(@NotNull Conversation.Mode mode, @Size(max = 200) String topic) {
    }

    record MessageRequest(@NotBlank @Size(max = 4000) String text) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    ConversationView start(@AuthenticationPrincipal CurrentUser user, @Valid @RequestBody StartRequest req) {
        return tutor.start(user, req.mode(), req.topic());
    }

    @GetMapping
    List<ConversationSummary> list(@AuthenticationPrincipal CurrentUser user) {
        return tutor.list(user);
    }

    @GetMapping("/{id}")
    ConversationView get(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return tutor.get(user, id);
    }

    @PostMapping("/{id}/close")
    ConversationView close(@AuthenticationPrincipal CurrentUser user, @PathVariable String id) {
        return tutor.close(user, id);
    }

    @PostMapping(path = "/{id}/messages", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    SseEmitter send(@AuthenticationPrincipal CurrentUser user, @PathVariable String id,
            @Valid @RequestBody MessageRequest req) {
        PreparedTurn turn = tutor.prepareTurn(user, id, req.text()); // errores → respuesta HTTP con Problem Details
        SseEmitter emitter = new SseEmitter(180_000L);
        executor.submit(() -> {
            try {
                TurnResult result = tutor.executeTurn(turn, delta -> send(emitter, "delta", Map.of("text", delta)));
                send(emitter, "done", result);
                emitter.complete();
            } catch (LlmException ex) {
                log.warn("Fallo del proveedor de IA en la conversación {}: {}", turn.publicId(), ex.getMessage());
                send(emitter, "error", Map.of("code", "AI_PROVIDER_ERROR",
                        "message", "El profesor no pudo responder. No se cobró este mensaje; inténtalo de nuevo."));
                emitter.complete();
            } catch (ApiException ex) {
                send(emitter, "error", Map.of("code", ex.code(), "message", ex.getMessage()));
                emitter.complete();
            } catch (RuntimeException ex) {
                log.error("Error inesperado en el turno de la conversación {}", turn.publicId(), ex);
                send(emitter, "error", Map.of("code", "INTERNAL_ERROR", "message", "Ocurrió un error inesperado."));
                emitter.complete();
            }
        });
        return emitter;
    }

    private static void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            // El cliente cerró la conexión: el turno sigue y su uso se registra igualmente (el consumo existió).
        }
    }
}
