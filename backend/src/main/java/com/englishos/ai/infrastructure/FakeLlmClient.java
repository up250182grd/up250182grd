package com.englishos.ai.infrastructure;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.englishos.ai.api.LlmClient;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Profesor SIMULADO: respuestas deterministas, sin red y sin costo real. Permite usar y probar el flujo completo
 * (streaming, correcciones, medición y cobro con tarifas ficticias) sin clave de API. La interfaz lo etiqueta como
 * simulado; nunca se presenta como IA real.
 */
@Component
@ConditionalOnProperty(name = "app.ai.provider", havingValue = "fake", matchIfMissing = true)
class FakeLlmClient implements LlmClient {

    static final String MARKER = "<<<CORRECTIONS>>>";

    private record Rule(Pattern pattern, String correction, String es, String en) {
    }

    private static final List<Rule> RULES = List.of(
            new Rule(Pattern.compile("\\bI is\\b", Pattern.CASE_INSENSITIVE), "I am",
                    "Con I se usa am: I am.", "With I we use am: I am."),
            new Rule(Pattern.compile("\\b(he|she|it) don't\\b", Pattern.CASE_INSENSITIVE), "$1 doesn't",
                    "Con he/she/it el negativo es doesn't.", "With he/she/it the negative is doesn't."),
            new Rule(Pattern.compile("\\b(he|she|it) have\\b", Pattern.CASE_INSENSITIVE), "$1 has",
                    "Con he/she/it se usa has.", "With he/she/it we use has."),
            new Rule(Pattern.compile("\\bI am agree\\b", Pattern.CASE_INSENSITIVE), "I agree",
                    "agree ya es un verbo: I agree (sin am).", "agree is already a verb: I agree (no am)."),
            new Rule(Pattern.compile("\\bpeoples\\b", Pattern.CASE_INSENSITIVE), "people",
                    "people ya es plural.", "people is already plural."));

    @Override
    public String provider() {
        return "FAKE";
    }

    @Override
    public ChatResult stream(ChatRequest request, Consumer<String> onDelta) {
        String last = request.messages().stream()
                .filter(m -> m.role() == Role.USER)
                .reduce((a, b) -> b)
                .map(Message::content)
                .orElse("");
        List<String> corrections = new ArrayList<>();
        for (Rule rule : RULES) {
            Matcher m = rule.pattern().matcher(last);
            if (m.find()) {
                corrections.add("{\"original\":" + quote(m.group()) + ",\"correction\":"
                        + quote(rule.pattern().matcher(m.group()).replaceFirst(rule.correction()))
                        + ",\"explanation_es\":" + quote(rule.es()) + ",\"explanation_en\":" + quote(rule.en())
                        + ",\"category\":\"GRAMMAR\"}");
            }
        }
        String reply = corrections.isEmpty()
                ? "Great! (simulated tutor) Your sentence is clear. Can you tell me a bit more about it? "
                        + "Try to use at least two sentences."
                : "Good effort! (simulated tutor) I noticed a small mistake; check the correction below. "
                        + "Now, could you say it again using the correct form?";
        String full = reply + "\n" + MARKER + "[" + String.join(",", corrections) + "]";
        for (String word : full.split("(?<= )")) {
            onDelta.accept(word);
        }
        long in = request.messages().stream().mapToLong(msg -> msg.content().length()).sum() / 4 + 1;
        long out = Math.min(full.length() / 4 + 1, request.maxOutputTokens());
        Map<String, Long> units = new LinkedHashMap<>();
        units.put("text_in", in);
        units.put("text_out", out);
        return new ChatResult("fake-" + UUID.randomUUID(), full, units, "SIMULATED");
    }

    private static String quote(String s) {
        return "\"" + s.replace("\\", "\\\\").replace("\"", "\\\"") + "\"";
    }
}
