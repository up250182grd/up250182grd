package com.englishos.ai.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.englishos.ai.api.LlmClient.LlmException;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Flujo SSE de ejemplo con la forma esperada de Chat Completions + include_usage (no grabado de la API real). */
class OpenAiStreamParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void collectsDeltasAndBillableUsageWithCachedTokensSeparated() {
        List<String> deltas = new ArrayList<>();
        OpenAiStreamParser p = new OpenAiStreamParser(JSON, deltas::add);
        for (String line : List.of(
                ": keep-alive",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{\"role\":\"assistant\",\"content\":\"\"}}]}",
                "",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\"Hello\"}}]}",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{\"content\":\" there!\"}}]}",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[{\"index\":0,\"delta\":{},\"finish_reason\":\"stop\"}]}",
                "data: {\"id\":\"chatcmpl-1\",\"choices\":[],\"usage\":{\"prompt_tokens\":120,\"completion_tokens\":7,"
                        + "\"total_tokens\":127,\"prompt_tokens_details\":{\"cached_tokens\":100}}}",
                "data: [DONE]")) {
            p.accept(line);
        }
        assertThat(deltas).containsExactly("Hello", " there!");
        assertThat(p.text()).isEqualTo("Hello there!");
        assertThat(p.responseId()).isEqualTo("chatcmpl-1");
        assertThat(p.units()).isEqualTo(Map.of("text_in", 20L, "text_in_cached", 100L, "text_out", 7L));
        assertThat(p.isDone()).isTrue();
    }

    @Test
    void missingUsageIsReportedAsNullNotGuessed() {
        OpenAiStreamParser p = new OpenAiStreamParser(JSON, d -> { });
        p.accept("data: {\"id\":\"x\",\"choices\":[{\"delta\":{\"content\":\"Hi\"}}]}");
        p.accept("data: [DONE]");
        assertThat(p.units()).isNull();
    }

    @Test
    void providerErrorsInTheStreamAreRaised() {
        OpenAiStreamParser p = new OpenAiStreamParser(JSON, d -> { });
        assertThatThrownBy(() -> p.accept("data: {\"error\":{\"message\":\"Rate limit\"}}"))
                .isInstanceOf(LlmException.class).hasMessageContaining("Rate limit");
    }
}
