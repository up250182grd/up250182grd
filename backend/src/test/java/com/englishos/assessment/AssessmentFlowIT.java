package com.englishos.assessment;

import java.util.Map;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-ASM: diagnóstico adaptativo de punta a punta, plan generado y protección contra respuestas fuera de turno. */
class AssessmentFlowIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    JdbcTemplate jdbc;

    private Object answerFor(JsonNode item, boolean correct) {
        String id = item.path("publicId").asString();
        JsonNode solution = JSON.readTree(jdbc.queryForObject(
                "select solution from exercises where public_id = ?", String.class, id));
        return switch (item.path("type").asString()) {
            case "MULTIPLE_CHOICE" -> {
                String right = solution.path("correctOptionId").asString();
                String chosen = right;
                if (!correct) {
                    for (JsonNode o : item.path("prompt").path("options")) {
                        if (!o.path("id").asString().equals(right)) {
                            chosen = o.path("id").asString();
                            break;
                        }
                    }
                }
                yield Map.of("optionId", chosen);
            }
            case "TRUE_FALSE" -> Map.of("value", correct == solution.path("value").booleanValue());
            case "FILL_BLANK" -> Map.of("values", java.util.List.of(
                    correct ? solution.path("answers").get(0).get(0).asString() : "xyz"));
            default -> throw new IllegalStateException("Tipo no esperado en el banco: " + item.path("type"));
        };
    }

    /** Responde todo el diagnóstico; devuelve el id de sesión. */
    private String runDiagnostic(ApiClient c, boolean alwaysCorrect) {
        var started = c.post("/api/v1/assessments", null);
        assertThat(started.status()).isEqualTo(200);
        String sessionId = started.json().path("sessionId").asString();
        int max = started.json().path("maxQuestions").asInt();
        for (int i = 0; i <= max; i++) {
            JsonNode next = c.post("/api/v1/assessments/" + sessionId + "/next", null).json();
            if (next.path("done").asBoolean()) {
                return sessionId;
            }
            JsonNode item = next.path("item");
            assertThat(next.toString()).doesNotContain("solution", "correctOptionId");
            var r = c.post("/api/v1/assessments/" + sessionId + "/responses",
                    Map.of("itemId", item.path("publicId").asString(), "answer", answerFor(item, alwaysCorrect)));
            assertThat(r.status()).as(r.body()).isEqualTo(200);
            assertThat(r.body()).doesNotContain("\"correct\"");
        }
        throw new AssertionError("El diagnóstico no terminó dentro del máximo de preguntas");
    }

    @Test
    void strongStudentIsPlacedAtB2AndGetsAPlanAndOnboardingCompletes() {
        ApiClient c = verifiedStudent(uniqueEmail());
        assertThat(c.get("/api/v1/me").json().path("profile").path("onboardingCompleted").asBoolean()).isFalse();

        String sessionId = runDiagnostic(c, true);
        var result = c.get("/api/v1/assessments/" + sessionId + "/result");
        assertThat(result.status()).isEqualTo(200);
        JsonNode r = result.json();
        assertThat(r.path("skills").size()).isEqualTo(3);
        r.path("skills").forEach(s -> assertThat(s.path("level").asString()).isEqualTo("B2"));
        assertThat(r.path("planStartLevel").asString()).isEqualTo("B2");
        assertThat(r.path("disclaimerEs").asString()).contains("No es una certificación oficial");

        assertThat(c.get("/api/v1/me/skills").json().size()).isEqualTo(3);
        assertThat(c.get("/api/v1/me").json().path("profile").path("onboardingCompleted").asBoolean()).isTrue();
        // Aún no hay contenido B2: el plan asigna el curso publicado más avanzado, sin inventar contenido.
        var summary = c.get("/api/v1/me/learning-summary").json();
        assertThat(summary.path("planStartLevel").asString()).isEqualTo("B2");
        assertThat(summary.path("recommended").path("level").asString()).isEqualTo("A2");
    }

    @Test
    void beginnerIsPlacedAtA1AndPlanStartsAtTheFirstLesson() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String sessionId = runDiagnostic(c, false);
        JsonNode r = c.get("/api/v1/assessments/" + sessionId + "/result").json();
        r.path("skills").forEach(s -> {
            assertThat(s.path("level").asString()).isEqualTo("A1");
            assertThat(s.path("confidence").asString()).isEqualTo("LOW");
        });
        var summary = c.get("/api/v1/me/learning-summary").json();
        assertThat(summary.path("recommended").path("level").asString()).isEqualTo("A1");
        assertThat(c.get("/api/v1/courses").body()).contains("\"inPlan\":true");
    }

    @Test
    void onlyTheCurrentQuestionCanBeAnsweredAndReloadKeepsIt() {
        ApiClient c = verifiedStudent(uniqueEmail());
        String sessionId = c.post("/api/v1/assessments", null).json().path("sessionId").asString();
        JsonNode first = c.post("/api/v1/assessments/" + sessionId + "/next", null).json().path("item");
        JsonNode reloaded = c.post("/api/v1/assessments/" + sessionId + "/next", null).json().path("item");
        assertThat(reloaded.path("publicId").asString()).isEqualTo(first.path("publicId").asString());

        String otherItem = jdbc.queryForObject(
                "select public_id from exercises where purpose = 'DIAGNOSTIC' and public_id <> ? limit 1",
                String.class, first.path("publicId").asString());
        var wrongItem = c.post("/api/v1/assessments/" + sessionId + "/responses",
                Map.of("itemId", otherItem, "answer", Map.of("optionId", "a")));
        assertThat(wrongItem.status()).isEqualTo(409);
        assertThat(wrongItem.code()).isEqualTo("NOT_CURRENT_QUESTION");

        // Reenviar la misma respuesta dos veces: la segunda ya no es la pregunta actual.
        Map<String, Object> body = Map.of("itemId", first.path("publicId").asString(), "answer", answerFor(first, true));
        assertThat(c.post("/api/v1/assessments/" + sessionId + "/responses", body).status()).isEqualTo(200);
        assertThat(c.post("/api/v1/assessments/" + sessionId + "/responses", body).status()).isEqualTo(409);

        // Iniciar de nuevo reanuda la misma sesión en curso.
        assertThat(c.post("/api/v1/assessments", null).json().path("sessionId").asString()).isEqualTo(sessionId);
    }

    @Test
    void sessionsAreOwnedByTheirStudent() {
        ApiClient me = verifiedStudent(uniqueEmail());
        ApiClient other = verifiedStudent(uniqueEmail());
        String sessionId = me.post("/api/v1/assessments", null).json().path("sessionId").asString();
        assertThat(other.post("/api/v1/assessments/" + sessionId + "/next", null).status()).isEqualTo(404);
        assertThat(other.get("/api/v1/assessments/" + sessionId + "/result").status()).isEqualTo(404);
    }
}
