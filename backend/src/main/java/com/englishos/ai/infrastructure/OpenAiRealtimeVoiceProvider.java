package com.englishos.ai.infrastructure;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.LlmClient.LlmException;
import com.englishos.ai.api.RealtimeVoiceProvider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Adaptador de OpenAI Realtime por WebRTC con control desde el servidor (ADR-0004):
 * <ol>
 *   <li>El navegador genera la oferta SDP; el backend la envía a {@code POST /v1/realtime/calls} como multipart
 *       ({@code sdp} + {@code session}) con la clave del servidor y devuelve la respuesta SDP al navegador.</li>
 *   <li>El {@code call_id} se toma de la cabecera {@code Location} de esa respuesta.</li>
 *   <li>El backend abre el canal lateral {@code wss://api.openai.com/v1/realtime?call_id=…} con su clave y recibe
 *       {@code response.done} con el consumo.</li>
 * </ol>
 * 🔍 NO probado contra la API real (sin acceso desde el entorno de desarrollo; la guía oficial no fue accesible).
 * Verificar endpoints, campos de {@code session} y la cabecera {@code Location} antes de activarlo.
 */
@Component
@ConditionalOnProperty(name = "app.ai.voice-provider", havingValue = "openai")
class OpenAiRealtimeVoiceProvider implements RealtimeVoiceProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiRealtimeVoiceProvider.class);

    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final JsonMapper json;
    private final String apiKey;
    private final String baseUrl;
    private final String wsUrl;

    OpenAiRealtimeVoiceProvider(JsonMapper json, @Value("${app.ai.openai.api-key:}") String apiKey,
            @Value("${app.ai.openai.base-url:https://api.openai.com/v1}") String baseUrl,
            @Value("${app.ai.openai.realtime-ws-url:wss://api.openai.com/v1/realtime}") String wsUrl) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException("AI_VOICE_PROVIDER=openai requiere OPENAI_API_KEY");
        }
        this.json = json;
        this.apiKey = apiKey;
        this.baseUrl = baseUrl;
        this.wsUrl = wsUrl;
    }

    @Override
    public String provider() {
        return "OPENAI";
    }

    @Override
    public boolean simulated() {
        return false;
    }

    @Override
    public CallStarted startCall(CallRequest request) {
        if (request.sdpOffer() == null || !request.sdpOffer().startsWith("v=")) {
            throw new LlmException("Oferta SDP inválida");
        }
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("type", "realtime");
        session.put("model", request.model());
        session.put("instructions", request.instructions());
        session.put("max_output_tokens", request.maxOutputTokens());
        session.put("audio", Map.of(
                "input", Map.of(
                        "transcription", Map.of("model", "gpt-4o-mini-transcribe"),
                        "turn_detection", Map.of("type", "semantic_vad", "interrupt_response", true)),
                "output", Map.of("voice", request.voice())));

        String boundary = "englishos-" + UUID.randomUUID();
        String body = "--" + boundary + "\r\nContent-Disposition: form-data; name=\"sdp\"\r\n\r\n" + request.sdpOffer()
                + "\r\n--" + boundary + "\r\nContent-Disposition: form-data; name=\"session\"\r\n"
                + "Content-Type: application/json\r\n\r\n" + json.writeValueAsString(session) + "\r\n--" + boundary
                + "--\r\n";
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + "/realtime/calls"))
                .timeout(Duration.ofSeconds(20))
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                .build();
        try {
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) {
                log.warn("OpenAI Realtime respondió {} al crear la llamada", res.statusCode());
                throw new LlmException("El proveedor de voz rechazó la llamada (" + res.statusCode() + ")");
            }
            String location = res.headers().firstValue("Location").orElse("");
            String callId = location.substring(location.lastIndexOf('/') + 1);
            if (callId.isBlank()) {
                // Sin call_id no hay canal lateral ni medición confiable: no se permite la llamada (ADR-0004).
                throw new LlmException("El proveedor no devolvió el identificador de la llamada");
            }
            return new CallStarted(callId, res.body());
        } catch (IOException e) {
            throw new LlmException("No se pudo crear la llamada de voz", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new LlmException("Creación de la llamada interrumpida", e);
        }
    }

    @Override
    public Sideband openSideband(String callId, Listener listener) {
        OpenAiRealtimeEvents events = new OpenAiRealtimeEvents(json, listener);
        WebSocket ws = http.newWebSocketBuilder()
                .header("Authorization", "Bearer " + apiKey)
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(wsUrl + "?call_id=" + callId), new WebSocket.Listener() {
                    private final StringBuilder buffer = new StringBuilder();

                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buffer.append(data);
                        if (last) {
                            try {
                                events.accept(buffer.toString());
                            } catch (RuntimeException ex) {
                                log.warn("Evento Realtime no interpretable: {}", ex.getMessage());
                            }
                            buffer.setLength(0);
                        }
                        webSocket.request(1);
                        return null;
                    }

                    @Override
                    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
                        listener.onClosed("SIDEBAND_CLOSED");
                        return null;
                    }

                    @Override
                    public void onError(WebSocket webSocket, Throwable error) {
                        log.warn("Canal lateral Realtime con error: {}", error.getClass().getSimpleName());
                        listener.onClosed("SIDEBAND_ERROR");
                    }
                }).join();
        return new Sideband() {
            @Override
            public void sendJson(String event) {
                ws.sendText(event, true);
            }

            @Override
            public void close() {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "fin");
            }
        };
    }

    @Override
    public void hangup(String callId) {
        try {
            http.send(HttpRequest.newBuilder(URI.create(baseUrl + "/realtime/calls/" + callId + "/hangup"))
                    .timeout(Duration.ofSeconds(10))
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.discarding());
        } catch (IOException e) {
            log.warn("No se pudo colgar la llamada {} en el proveedor", callId);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
