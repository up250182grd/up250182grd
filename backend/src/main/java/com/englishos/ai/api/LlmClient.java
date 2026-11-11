package com.englishos.ai.api;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Puerto hacia un modelo de lenguaje de texto. Sin lógica de negocio: recibe mensajes, devuelve texto y el uso que
 * informa el proveedor (en unidades tarificables: text_in, text_in_cached, text_out). Sustituible sin tocar el tutor.
 */
public interface LlmClient {

    enum Role { SYSTEM, USER, ASSISTANT }

    record Message(Role role, String content) {
    }

    record ChatRequest(String model, List<Message> messages, int maxOutputTokens) {
    }

    /**
     * @param responseId identificador de la respuesta en el proveedor (clave de idempotencia del uso)
     * @param units      uso informado por el proveedor (nunca calculado en el navegador)
     * @param dataSource PROVIDER_RESPONSE para proveedores reales, SIMULATED para el simulador
     */
    record ChatResult(String responseId, String text, Map<String, Long> units, String dataSource) {
    }

    /** Proveedor tal como figura en el catálogo de modelos (FAKE, OPENAI). */
    String provider();

    /** Genera la respuesta en streaming: {@code onDelta} recibe fragmentos de texto conforme llegan. */
    ChatResult stream(ChatRequest request, Consumer<String> onDelta);

    /** Error del proveedor (red, cuota, respuesta inválida). Nunca contiene la clave de API. */
    class LlmException extends RuntimeException {

        public LlmException(String message) {
            super(message);
        }

        public LlmException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
