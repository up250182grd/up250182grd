package com.englishos.ai.infrastructure;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.LlmClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de OpenAI Chat Completions con streaming. La clave vive solo en el servidor (variable OPENAI_API_KEY) y
 * nunca se registra en logs. 🔍 Nombres de parámetros y del objeto {@code usage} según la documentación pública
 * conocida; solo se prueban con un flujo de ejemplo (OpenAiStreamParserTest), no contra la API real. Verificar
 * con la documentación vigente antes de activar AI_PROVIDER=openai.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "openai")
class OpenAiChatClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(OpenAiChatClient.class);

    private final HttpClient http;
    private final JsonMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final Duration timeout;

    OpenAiChatClient(JsonMapper json, @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.openai.timeout:60s}") Duration timeout) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_PROVIDER=openai requiere OPENAI_API_KEY");
        }
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.timeout = timeout;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    @Override
    public String provider() {
        return "OPENAI";
    }

    @Override
    public ChatResult stream(ChatRequest request, Consumer<String> onDelta) {
        List<Map<String, String>> messages = new ArrayList<>();
        for (Message m : request.messages()) {
            messages.add(Map.of("role", m.role().name().toLowerCase(Locale.ROOT), "content", m.content()));
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", request.model());
        body.put("messages", messages);
        body.put("stream", true);
        body.put("stream_options", Map.of("include_usage", true));
        body.put("max_completion_tokens", request.maxOutputTokens());

        HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(baseUrl + "/chat/completions"))
                .timeout(timeout)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(body)))
                .build();
        try {
            HttpResponse<InputStream> response = http.send(httpRequest, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String err;
                try (InputStream in = response.body()) {
                    err = new String(in.readNBytes(2000), StandardCharsets.UTF_8);
                }
                log.warn("OpenAI respondió {}: {}", response.statusCode(), err.replace(apiKey, "***"));
                throw new LlmException("El proveedor de IA respondió con estado " + response.statusCode());
            }
            OpenAiStreamParser parser = new OpenAiStreamParser(json, onDelta);
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null && !parser.isDone()) {
                    parser.accept(line);
                }
            }
            if (parser.units() == null || parser.responseId() == null) {
                // Sin uso informado no se puede cobrar con certeza: el tutor lo registra como incidencia.
                throw new LlmException("La respuesta del proveedor no incluyó el uso de tokens");
            }
            return new ChatResult(parser.responseId(), parser.text(), parser.units(), "PROVIDER_RESPONSE");
        } catch (IOException e) {
            throw new LlmException("No se pudo comunicar con el proveedor de IA", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Solicitud al proveedor interrumpida", e);
        }
    }
}
