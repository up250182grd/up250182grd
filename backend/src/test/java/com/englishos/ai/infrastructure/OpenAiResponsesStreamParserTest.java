package com.englishos.ai.infrastructure;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.englishos.ai.api.LlmClient.ChatRequest;
import com.englishos.ai.api.LlmClient.LlmException;
import com.englishos.ai.api.LlmClient.Message;
import com.englishos.ai.api.LlmClient.Role;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Flujo SSE de ejemplo con la forma de la Responses API descrita en la referencia oficial (eventos
 * {@code event:} + {@code data:}); no está grabado de una llamada real.
 */
class OpenAiResponsesStreamParserTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private static void feed(OpenAiResponsesStreamParser p, String type, String json) {
        p.accept("event: " + type);
        p.accept("data: " + json);
        p.accept("");
    }

    @Test
    void collectsDeltasAndBillableUsageWithCachedTokensSeparated() {
        List<String> deltas = new ArrayList<>();
        OpenAiResponsesStreamParser p = new OpenAiResponsesStreamParser(JSON, deltas::add);
        p.accept(": keep-alive");
        feed(p, "response.created", "{\"type\":\"response.created\",\"sequence_number\":0,"
                + "\"response\":{\"id\":\"resp_1\",\"status\":\"in_progress\",\"usage\":null}}");
        feed(p, "response.output_item.added", "{\"type\":\"response.output_item.added\",\"sequence_number\":1}");
        feed(p, "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"sequence_number\":2,"
                + "\"item_id\":\"msg_1\",\"output_index\":0,\"content_index\":0,\"delta\":\"Hello\"}");
        feed(p, "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"sequence_number\":3,"
                + "\"delta\":\" there!\"}");
        feed(p, "response.output_text.done", "{\"type\":\"response.output_text.done\",\"text\":\"Hello there!\"}");
        assertThat(p.isDone()).isFalse();
        feed(p, "response.completed", "{\"type\":\"response.completed\",\"sequence_number\":9,"
                + "\"response\":{\"id\":\"resp_1\",\"status\":\"completed\",\"usage\":{\"input_tokens\":120,"
                + "\"input_tokens_details\":{\"cached_tokens\":100,\"cache_write_tokens\":0},\"output_tokens\":7,"
                + "\"output_tokens_details\":{\"reasoning_tokens\":0},\"total_tokens\":127}}}");

        assertThat(deltas).containsExactly("Hello", " there!");
        assertThat(p.text()).isEqualTo("Hello there!");
        assertThat(p.responseId()).isEqualTo("resp_1");
        assertThat(p.units()).isEqualTo(Map.of("text_in", 20L, "text_in_cached", 100L, "text_out", 7L));
        assertThat(p.incomplete()).isFalse();
        assertThat(p.isDone()).isTrue();
    }

    @Test
    void incompleteResponsesKeepTheirTextAndAreStillBilled() {
        OpenAiResponsesStreamParser p = new OpenAiResponsesStreamParser(JSON, d -> { });
        feed(p, "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Partial\"}");
        feed(p, "response.incomplete", "{\"type\":\"response.incomplete\",\"response\":{\"id\":\"resp_2\","
                + "\"status\":\"incomplete\",\"incomplete_details\":{\"reason\":\"max_output_tokens\"},"
                + "\"usage\":{\"input_tokens\":50,\"output_tokens\":600,\"total_tokens\":650}}}");
        assertThat(p.incomplete()).isTrue();
        assertThat(p.isDone()).isTrue();
        assertThat(p.text()).isEqualTo("Partial");
        assertThat(p.units()).isEqualTo(Map.of("text_in", 50L, "text_in_cached", 0L, "text_out", 600L));
    }

    @Test
    void missingUsageIsReportedAsNullNotGuessed() {
        OpenAiResponsesStreamParser p = new OpenAiResponsesStreamParser(JSON, d -> { });
        feed(p, "response.output_text.delta", "{\"type\":\"response.output_text.delta\",\"delta\":\"Hi\"}");
        feed(p, "response.completed", "{\"type\":\"response.completed\",\"response\":{\"id\":\"resp_3\"}}");
        assertThat(p.units()).isNull();
    }

    @Test
    void providerErrorsInTheStreamAreRaised() {
        OpenAiResponsesStreamParser p = new OpenAiResponsesStreamParser(JSON, d -> { });
        assertThatThrownBy(() -> p.accept("data: {\"type\":\"error\",\"code\":\"rate_limit_exceeded\","
                + "\"message\":\"Rate limit\",\"param\":null,\"sequence_number\":1}"))
                .isInstanceOf(LlmException.class).hasMessageContaining("Rate limit");

        OpenAiResponsesStreamParser failed = new OpenAiResponsesStreamParser(JSON, d -> { });
        assertThatThrownBy(() -> failed.accept("data: {\"type\":\"response.failed\",\"response\":{\"id\":\"r\","
                + "\"status\":\"failed\",\"error\":{\"code\":\"server_error\",\"message\":\"Boom\"}}}"))
                .isInstanceOf(LlmException.class).hasMessageContaining("Boom");
    }

    @Test
    void requestBodyUsesResponsesFieldsAndDoesNotStoreConversations() {
        Map<String, Object> body = OpenAiResponsesClient.requestBody(new ChatRequest("gpt-6.1-terra", List.of(
                new Message(Role.SYSTEM, "You are a tutor."), new Message(Role.USER, "Hi")), 600));
        assertThat(body).containsEntry("model", "gpt-6.1-terra").containsEntry("stream", true)
                .containsEntry("store", false).containsEntry("max_output_tokens", 600)
                .doesNotContainKeys("messages", "stream_options", "max_completion_tokens");
        assertThat(body.get("input")).isEqualTo(List.of(
                Map.of("role", "system", "content", "You are a tutor."), Map.of("role", "user", "content", "Hi")));
    }
}
