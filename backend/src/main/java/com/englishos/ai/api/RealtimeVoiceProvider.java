package com.englishos.ai.api;

import java.util.Map;

/**
 * Puerto hacia un proveedor de voz en tiempo real (ADR-0004). El audio viaja directo entre el navegador y el
 * proveedor por WebRTC; el SERVIDOR negocia la llamada con su clave y abre un canal lateral propio por el que recibe
 * el consumo. Así el cobro nunca depende de datos enviados por el navegador.
 */
public interface RealtimeVoiceProvider {

    /** Proveedor tal como figura en el catálogo de modelos (FAKE, OPENAI). */
    String provider();

    /** {@code true} si la llamada no lleva audio real (simulación para desarrollo y pruebas). */
    boolean simulated();

    /**
     * Modelo que transcribe lo que dice el alumno. Se factura aparte, con SU tarifa (no la del modelo realtime), así
     * que debe estar registrado en el catálogo con una tarifa vigente para poder iniciar llamadas.
     */
    String transcriptionModel();

    record CallRequest(String model, String instructions, String voice, int maxOutputTokens, String sdpOffer) {
    }

    /** {@code sdpAnswer} es null en el proveedor simulado (no hay conexión WebRTC real). */
    record CallStarted(String callId, String sdpAnswer) {
    }

    /** Eventos del canal lateral. Se invocan desde el hilo del proveedor. */
    interface Listener {

        /** Uso informado por el proveedor para una respuesta (unidades tarificables). */
        void onUsage(String responseId, Map<String, Long> units);

        /**
         * Uso de la transcripción de un turno del alumno ({@code itemId} identifica el turno), en unidades del modelo
         * de transcripción: {@code audio_microminutes} si se factura por duración, o tokens
         * ({@code audio_in}, {@code text_in}, {@code text_out}) si se factura por tokens.
         */
        void onTranscriptionUsage(String itemId, Map<String, Long> units);

        void onTranscript(String role, String text);

        /** El canal lateral se cerró (fin de la llamada, error de red o del proveedor). */
        void onClosed(String reason);
    }

    interface Sideband {

        /** Envía un evento al proveedor por el canal lateral (p. ej. pedir que el profesor se despida). */
        void sendJson(String event);

        void close();
    }

    CallStarted startCall(CallRequest request);

    Sideband openSideband(String callId, Listener listener);

    /** Cuelga la llamada en el proveedor (mejor esfuerzo; idempotente). */
    void hangup(String callId);
}
