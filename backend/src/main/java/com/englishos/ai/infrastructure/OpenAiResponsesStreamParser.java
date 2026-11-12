package com.englishos.ai.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.LlmClient.LlmException;

/**
 * Lee el flujo SSE de la Responses API ({@code POST /v1/responses} con {@code stream: true}). Cada evento llega en
 * una línea {@code data:} con un campo {@code type}:
 * <ul>
 *   <li>{@code response.created}: {@code response.id}.</li>
 *   <li>{@code response.output_text.delta}: fragmento de texto en {@code delta}.</li>
 *   <li>{@code response.completed} / {@code response.incomplete}: evento final; {@code response.usage} trae
 *       {@code input_tokens}, {@code input_tokens_details.cached_tokens} (subconjunto de la entrada) y
 *       {@code output_tokens} (incluye los de razonamiento, que se facturan como salida).</li>
 *   <li>{@code response.failed} y {@code error}: el proveedor no completó la respuesta.</li>
 * </ul>
 * Formato verificado el 2026-11-11 en la referencia oficial (developers.openai.com/api/reference/resources/responses
 * y la guía "Streaming API responses"). 🔍 Probado solo con un flujo de ejemplo, no con una llamada real.
 */
final class OpenAiResponsesStreamParser {

    private final JsonMapper json;
    private final Consumer<String> onDelta;
    private final StringBuilder text = new StringBuilder();
    private String responseId;
    private Map<String, Long> units;
    private boolean incomplete;
    private boolean done;

    OpenAiResponsesStreamParser(JsonMapper json, Consumer<String> onDelta) {
        this.json = json;
        this.onDelta = onDelta;
    }

    void accept(String line) {
        if (line == null || !line.startsWith("data:")) {
            return; // líneas vacías, comentarios ":" y "event:" (el tipo también viaja dentro del JSON)
        }
        String data = line.substring(5).trim();
        if (data.isEmpty() || data.equals("[DONE]")) {
            return;
        }
        JsonNode event = json.readTree(data);
        String type = event.path("type").asString("");
        switch (type) {
            case "response.created", "response.in_progress" -> rememberId(event.path("response"));
            case "response.output_text.delta" -> {
                String delta = event.path("delta").asString("");
                if (!delta.isEmpty()) {
                    text.append(delta);
                    onDelta.accept(delta);
                }
            }
            case "response.completed" -> finish(event.path("response"));
            case "response.incomplete" -> {
                // Se cortó (por ejemplo, max_output_tokens): el texto parcial y su uso son reales y se cobran.
                incomplete = true;
                finish(event.path("response"));
            }
            case "response.failed" -> {
                done = true;
                throw new LlmException("El proveedor no completó la respuesta: "
                        + event.path("response").path("error").path("message").asString("sin detalle"));
            }
            case "error" -> {
                done = true;
                throw new LlmException("El proveedor devolvió un error: " + event.path("message").asString(""));
            }
            default -> {
                // Otros eventos (partes de contenido, razonamiento, herramientas) no afectan al tutor de texto.
            }
        }
    }

    private void rememberId(JsonNode response) {
        if (response.path("id").isString()) {
            responseId = response.path("id").asString();
        }
    }

    private void finish(JsonNode response) {
        rememberId(response);
        JsonNode usage = response.path("usage");
        if (usage.isObject()) {
            long input = usage.path("input_tokens").asLong();
            long cached = usage.path("input_tokens_details").path("cached_tokens").asLong(0);
            Map<String, Long> u = new LinkedHashMap<>();
            u.put("text_in", Math.max(0, input - cached));
            u.put("text_in_cached", cached);
            u.put("text_out", usage.path("output_tokens").asLong());
            units = u;
        }
        done = true;
    }

    String text() {
        return text.toString();
    }

    String responseId() {
        return responseId;
    }

    /** Uso informado por el proveedor; {@code null} si el flujo terminó sin él (no se inventa). */
    Map<String, Long> units() {
        return units;
    }

    /** {@code true} si la respuesta terminó como {@code response.incomplete} (texto truncado). */
    boolean incomplete() {
        return incomplete;
    }

    boolean isDone() {
        return done;
    }
}
