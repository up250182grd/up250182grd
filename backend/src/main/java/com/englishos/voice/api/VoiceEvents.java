package com.englishos.voice.api;

import java.time.Instant;

/** Eventos de dominio de las llamadas de voz. */
public final class VoiceEvents {

    private VoiceEvents() {
    }

    /** La llamada terminó y quedó liquidada (una sola vez). */
    public record SessionEnded(Long userId, String sessionId, long connectedSeconds, int studentTurns, Instant at) {
    }
}
