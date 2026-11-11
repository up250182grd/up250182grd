package com.englishos.tutor.api;

import java.time.Instant;

/** Eventos de dominio del profesor por texto y de la práctica de errores. */
public final class TutorEvents {

    private TutorEvents() {
    }

    /** El alumno envió un mensaje (actividad del día). */
    public record MessageSent(Long userId, String conversationId, Instant at) {
    }

    /** Se cerró una conversación (una sola vez). {@code turns} = respuestas del profesor. */
    public record ConversationClosed(Long userId, String conversationId, int turns, Instant at) {
    }

    /** El alumno superó un error tras practicarlo. */
    public record ErrorResolved(Long userId, String errorId, Instant at) {
    }
}
