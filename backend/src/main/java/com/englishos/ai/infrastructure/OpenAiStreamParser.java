package com.englishos.ai.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.LlmClient.LlmException;

/**
 * Lee el flujo SSE de Chat Completions con {@code stream_options.include_usage = true}: fragmentos en
 * {@code choices[0].delta.content} y, en el último fragmento, {@code usage} con los tokens facturables.
 * 🔍 Formato tomado de la documentación pública conocida al escribirlo; NO se ha probado contra la API real en
 * este entorno. Verificar con la documentación vigente y una llamada real antes de usarlo en producción.
 */
final class OpenAiStreamParser {

    private final JsonMapper json;
    private final Consumer<String> onDelta;
    private final StringBuilder text = new StringBuilder();
    private String responseId;
    private Map<String, Long> units;
    private boolean done;

    OpenAiStreamParser(JsonMapper json, Consumer<String> onDelta) {
        this.json = json;
        this.onDelta = onDelta;
    }

    void accept(String line) {
        if (line == null || !line.startsWith("data:")) {
            return; // líneas vacías, comentarios ":" o "event:" no aportan datos
        }
        String data = line.substring(5).trim();
        if (data.equals("[DONE]")) {
            done = true;
            return;
        }
        JsonNode chunk = json.readTree(data);
        if (chunk.has("error")) {
            throw new LlmException("El proveedor devolvió un error: " + chunk.path("error").path("message").asString());
        }
        if (chunk.path("id").isString()) {
            responseId = chunk.path("id").asString();
        }
        JsonNode content = chunk.path("choices").path(0).path("delta").path("content");
        if (content.isString() && !content.asString().isEmpty()) {
            text.append(content.asString());
            onDelta.accept(content.asString());
        }
        JsonNode usage = chunk.path("usage");
        if (usage.isObject()) {
            long prompt = usage.path("prompt_tokens").asLong();
            long cached = usage.path("prompt_tokens_details").path("cached_tokens").asLong(0);
            Map<String, Long> u = new LinkedHashMap<>();
            u.put("text_in", Math.max(0, prompt - cached));
            u.put("text_in_cached", cached);
            u.put("text_out", usage.path("completion_tokens").asLong());
            units = u;
        }
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

    boolean isDone() {
        return done;
    }
}
