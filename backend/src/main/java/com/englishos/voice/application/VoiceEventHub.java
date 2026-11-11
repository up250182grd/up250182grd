package com.englishos.voice.application;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/** Difunde a la interfaz, por Server-Sent Events, el medidor, la transcripción en vivo y el fin de cada llamada. */
@Component
public class VoiceEventHub {

    private final Map<Long, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter subscribe(Long sessionId, long timeoutMillis) {
        SseEmitter emitter = new SseEmitter(timeoutMillis);
        List<SseEmitter> list = emitters.computeIfAbsent(sessionId, k -> new CopyOnWriteArrayList<>());
        list.add(emitter);
        Runnable remove = () -> list.remove(emitter);
        emitter.onCompletion(remove);
        emitter.onTimeout(remove);
        emitter.onError(e -> remove.run());
        return emitter;
    }

    public void publish(Long sessionId, String event, Object data) {
        for (SseEmitter emitter : emitters.getOrDefault(sessionId, List.of())) {
            send(emitter, event, data);
        }
    }

    public void send(SseEmitter emitter, String event, Object data) {
        try {
            emitter.send(SseEmitter.event().name(event).data(data, MediaType.APPLICATION_JSON));
        } catch (IOException | IllegalStateException ex) {
            emitter.completeWithError(ex);
        }
    }

    public void complete(Long sessionId) {
        List<SseEmitter> list = emitters.remove(sessionId);
        if (list != null) {
            list.forEach(SseEmitter::complete);
        }
    }
}
