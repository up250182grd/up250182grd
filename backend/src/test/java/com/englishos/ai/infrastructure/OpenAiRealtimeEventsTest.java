package com.englishos.ai.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.englishos.ai.api.RealtimeVoiceProvider.Listener;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Eventos de ejemplo con la forma esperada del canal lateral (no grabados de la API real). */
class OpenAiRealtimeEventsTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static final class Recorder implements Listener {
        final List<String> log = new ArrayList<>();
        Map<String, Long> lastUnits;

        @Override
        public void onUsage(String responseId, Map<String, Long> units) {
            log.add("usage:" + responseId);
            lastUnits = units;
        }

        @Override
        public void onTranscript(String role, String text) {
            log.add(role + ":" + text);
        }

        @Override
        public void onClosed(String reason) {
            log.add("closed:" + reason);
        }
    }

    @Test
    void responseDoneUsageIsSplitByModalityAndCache() {
        Recorder r = new Recorder();
        new OpenAiRealtimeEvents(JSON, r).accept("""
                {"type":"response.done","response":{"id":"resp_1","status":"completed","usage":{
                  "total_tokens":700,"input_tokens":500,"output_tokens":200,
                  "input_token_details":{"text_tokens":300,"audio_tokens":200,"cached_tokens":250,
                    "cached_tokens_details":{"text_tokens":200,"audio_tokens":50}},
                  "output_token_details":{"text_tokens":40,"audio_tokens":160}}}}""");
        assertThat(r.log).containsExactly("usage:resp_1");
        assertThat(r.lastUnits).isEqualTo(Map.of("text_in", 100L, "text_in_cached", 200L, "audio_in", 150L,
                "audio_in_cached", 50L, "text_out", 40L, "audio_out", 160L));
    }

    @Test
    void missingCacheBreakdownIsAttributedToTextFirst() {
        var units = OpenAiRealtimeEvents.units(JSON.readTree("""
                {"input_token_details":{"text_tokens":100,"audio_tokens":50,"cached_tokens":120},
                 "output_token_details":{"text_tokens":0,"audio_tokens":30}}"""));
        assertThat(units).containsEntry("text_in_cached", 100L).containsEntry("audio_in_cached", 20L)
                .containsEntry("text_in", 0L).containsEntry("audio_in", 30L);
    }

    @Test
    void transcriptsAndIrrelevantEvents() {
        Recorder r = new Recorder();
        var events = new OpenAiRealtimeEvents(JSON, r);
        events.accept("{\"type\":\"conversation.item.input_audio_transcription.completed\",\"transcript\":\" I is happy \"}");
        events.accept("{\"type\":\"response.output_audio_transcript.done\",\"transcript\":\"You mean: I am happy!\"}");
        events.accept("{\"type\":\"input_audio_buffer.speech_started\"}");
        events.accept("{\"type\":\"response.done\",\"response\":{\"id\":\"resp_2\"}}"); // sin uso: no se inventa
        assertThat(r.log).containsExactly("USER:I is happy", "ASSISTANT:You mean: I am happy!");
    }
}
