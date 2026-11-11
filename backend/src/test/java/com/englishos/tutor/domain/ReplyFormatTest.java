package com.englishos.tutor.domain;

import java.util.List;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

class ReplyFormatTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Test
    void separatesVisibleTextFromValidatedCorrections() {
        var parsed = ReplyFormat.parse("""
                Nice try! What do you do on weekends?
                <<<CORRECTIONS>>>[{"original":"She have","correction":"She has","explanation_es":"has con she",
                "explanation_en":"has with she","category":"grammar"},
                {"original":"ok","correction":"ok","category":"OTHER"},
                {"original":"x","correction":"y","category":"<script>"}]""", JSON);
        assertThat(parsed.visibleText()).isEqualTo("Nice try! What do you do on weekends?");
        assertThat(parsed.corrections()).hasSize(2);
        assertThat(parsed.corrections().get(0).category()).isEqualTo("GRAMMAR");
        assertThat(parsed.corrections().get(1).category()).isEqualTo("OTHER"); // categoría desconocida
    }

    @Test
    void malformedOrMissingCorrectionsNeverBreakTheReply() {
        assertThat(ReplyFormat.parse("Hello!", JSON).corrections()).isEmpty();
        var broken = ReplyFormat.parse("Hello!\n<<<CORRECTIONS>>>[{not json", JSON);
        assertThat(broken.visibleText()).isEqualTo("Hello!");
        assertThat(broken.corrections()).isEmpty();
    }

    @Test
    void streamFilterNeverLeaksTheMarkerEvenWhenSplitAcrossChunks() {
        var filter = new ReplyFormat.StreamFilter();
        StringBuilder shown = new StringBuilder();
        for (String chunk : List.of("Good ", "job!\n<<<CORR", "ECTIONS>>>[{\"original\":", "\"a\"}]")) {
            shown.append(filter.accept(chunk));
        }
        shown.append(filter.flush());
        assertThat(shown.toString()).isEqualTo("Good job!\n");
    }

    @Test
    void streamFilterReleasesHeldTextThatWasNotTheMarker() {
        var filter = new ReplyFormat.StreamFilter();
        StringBuilder shown = new StringBuilder();
        shown.append(filter.accept("Use <<< "));
        shown.append(filter.accept("carefully"));
        shown.append(filter.flush());
        assertThat(shown.toString()).isEqualTo("Use <<< carefully");
    }
}
