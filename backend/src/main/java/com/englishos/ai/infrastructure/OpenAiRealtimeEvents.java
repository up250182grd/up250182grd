package com.englishos.ai.infrastructure;

import java.util.LinkedHashMap;
import java.util.Map;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import com.englishos.ai.api.RealtimeVoiceProvider.Listener;

/**
 * Interpreta los eventos del canal lateral de OpenAI Realtime y los traduce al puerto {@link Listener}.
 *
 * <p>🔍 Formas tomadas de la documentación pública y de los SDK oficiales (consultados por buscador el 2026-11-11;
 * la guía oficial no fue accesible desde el entorno). No probado contra la API real:
 * <ul>
 *   <li>{@code response.done}: {@code response.id} y {@code response.usage} con {@code input_token_details}
 *       ({@code text_tokens}, {@code audio_tokens}, {@code cached_tokens}, {@code cached_tokens_details}) y
 *       {@code output_token_details} ({@code text_tokens}, {@code audio_tokens}). Los tokens en caché son un
 *       subconjunto de los de entrada.</li>
 *   <li>Transcripciones: {@code conversation.item.input_audio_transcription.completed} (alumno, con
 *       {@code item_id} y {@code usage} de la transcripción, que se cobra aparte) y
 *       {@code response.output_audio_transcript.done} (profesor).</li>
 * </ul>
 */
final class OpenAiRealtimeEvents {

    private final JsonMapper json;
    private final Listener listener;

    OpenAiRealtimeEvents(JsonMapper json, Listener listener) {
        this.json = json;
        this.listener = listener;
    }

    void accept(String raw) {
        JsonNode event = json.readTree(raw);
        switch (event.path("type").asString()) {
            case "response.done" -> {
                JsonNode response = event.path("response");
                JsonNode usage = response.path("usage");
                if (usage.isObject() && response.path("id").isString()) {
                    listener.onUsage(response.path("id").asString(), units(usage));
                }
            }
            case "conversation.item.input_audio_transcription.completed" -> {
                transcript("USER", event.path("transcript"));
                JsonNode usage = event.path("usage");
                if (usage.isObject() && event.path("item_id").isString()) {
                    listener.onTranscriptionUsage(event.path("item_id").asString(), transcriptionUnits(usage));
                }
            }
            case "response.output_audio_transcript.done" -> transcript("ASSISTANT", event.path("transcript"));
            case "error" -> {
                // Los errores del proveedor no generan cobro; se registran por quien consuma el canal.
            }
            default -> {
                // Otros eventos (deltas de audio, VAD...) no afectan al cobro ni a la transcripción guardada.
            }
        }
    }

    /**
     * Convierte {@code usage} en unidades tarificables separando lo que estuvo en caché. Si el proveedor no informa
     * el desglose por modalidad de la caché, se atribuye al texto (la tarifa de caché más conservadora para el
     * alumno) — 🔍 confirmar con la factura real durante la conciliación.
     */
    static Map<String, Long> units(JsonNode usage) {
        JsonNode in = usage.path("input_token_details");
        JsonNode out = usage.path("output_token_details");
        long textIn = in.path("text_tokens").asLong(0);
        long audioIn = in.path("audio_tokens").asLong(0);
        long cached = in.path("cached_tokens").asLong(0);
        JsonNode cachedDetails = in.path("cached_tokens_details");
        long cachedText;
        long cachedAudio;
        if (cachedDetails.isObject()) {
            cachedText = cachedDetails.path("text_tokens").asLong(0);
            cachedAudio = cachedDetails.path("audio_tokens").asLong(0);
        } else {
            cachedText = Math.min(cached, textIn);
            cachedAudio = Math.min(cached - cachedText, audioIn);
        }
        long textOut = out.path("text_tokens").asLong(0);
        long audioOut = out.path("audio_tokens").asLong(0);
        if (!in.isObject() && !out.isObject()) {
            // Sin desglose por modalidad: se toma todo como audio (la tarifa más alta) para no subcobrar; queda
            // marcado para conciliación por el registro de uso.
            audioIn = usage.path("input_tokens").asLong(0);
            audioOut = usage.path("output_tokens").asLong(0);
        }
        Map<String, Long> units = new LinkedHashMap<>();
        units.put("text_in", Math.max(0, textIn - cachedText));
        units.put("text_in_cached", cachedText);
        units.put("audio_in", Math.max(0, audioIn - cachedAudio));
        units.put("audio_in_cached", cachedAudio);
        units.put("text_out", textOut);
        units.put("audio_out", audioOut);
        return units;
    }

    /**
     * Uso de la transcripción del alumno, que se factura con la tarifa del modelo de transcripción (no la del modelo
     * realtime). Según la referencia oficial (verificada el 2026-11-11) llega en una de dos formas:
     * <ul>
     *   <li>{@code {"type":"duration","seconds":2.4}} → {@code audio_microminutes} (millonésimas de minuto: con la
     *       tarifa capturada como precio por minuto, la calculadora da segundos / 60 × precio).</li>
     *   <li>{@code {"type":"tokens","input_tokens":…,"input_token_details":{"audio_tokens","text_tokens"},
     *       "output_tokens":…}} → {@code audio_in}, {@code text_in} y {@code text_out}. Sin desglose, toda la
     *       entrada se toma como audio (es lo que se transcribe).</li>
     * </ul>
     */
    static Map<String, Long> transcriptionUnits(JsonNode usage) {
        Map<String, Long> units = new LinkedHashMap<>();
        if ("duration".equals(usage.path("type").asString("")) || usage.path("seconds").isNumber()) {
            units.put(TranscriptionUnits.AUDIO_MICROMINUTES,
                    TranscriptionUnits.microminutes(usage.path("seconds").decimalValue()));
            return units;
        }
        JsonNode in = usage.path("input_token_details");
        long input = usage.path("input_tokens").asLong(0);
        long audioIn = in.isObject() ? in.path("audio_tokens").asLong(0) : input;
        long textIn = in.isObject() ? in.path("text_tokens").asLong(0) : 0;
        units.put("audio_in", audioIn);
        units.put("text_in", textIn);
        units.put("text_out", usage.path("output_tokens").asLong(0));
        return units;
    }

    private void transcript(String role, JsonNode text) {
        if (text.isString() && !text.asString().isBlank()) {
            listener.onTranscript(role, text.asString().trim());
        }
    }
}
