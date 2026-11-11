package com.englishos.assessment.api;

import java.time.Instant;

/** Eventos de dominio del diagnóstico. */
public final class AssessmentEvents {

    private AssessmentEvents() {
    }

    public record DiagnosticCompleted(Long userId, String sessionId, Instant at) {
    }
}
