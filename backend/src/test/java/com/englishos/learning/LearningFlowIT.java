package com.englishos.learning;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-CRS, RF-EXR y RN-40 de punta a punta contra MySQL real. */
class LearningFlowIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LESSON_SLUG = "a1-verb-to-be";

    @Autowired
    JdbcTemplate jdbc;

    private String lessonId() {
        return jdbc.queryForObject("select public_id from lessons where slug = ?", String.class, LESSON_SLUG);
    }

    /** Respuesta correcta construida a partir de la solución guardada (solo la prueba accede a ella). */
    private Map<String, Object> correctAnswer(String exercisePublicId) {
        Map<String, Object> row = jdbc.queryForMap(
                "select type, prompt, solution from exercises where public_id = ?", exercisePublicId);
        JsonNode solution = JSON.readTree(row.get("solution").toString());
        return switch (row.get("type").toString()) {
            case "MULTIPLE_CHOICE" -> Map.of("optionId", solution.path("correctOptionId").asString());
            case "TRUE_FALSE" -> Map.of("value", solution.path("value").booleanValue());
            case "FILL_BLANK" -> Map.of("values",
                    solution.path("answers").valueStream().map(a -> a.get(0).asString()).toList());
            case "WORD_ORDER" -> Map.of("tokens",
                    solution.path("sentences").get(0).valueStream().map(JsonNode::asString).toList());
            case "MATCHING" -> Map.of("pairs", JSON.convertValue(solution.path("pairs"), Map.class));
            case "ERROR_SPOTTING" -> Map.of("index", solution.path("errorIndex").intValue());
            default -> throw new IllegalStateException();
        };
    }

    private ApiClient.Response attempt(ApiClient c, String exerciseId, String runId, Object answer, String key) {
        return c.post("/api/v1/exercises/" + exerciseId + "/attempts", Map.of(
                "runId", runId, "answer", answer, "hintsUsed", 0, "durationMs", 1500, "idempotencyKey", key));
    }

    private static String key() {
        return UUID.randomUUID().toString();
    }

    @Test
    void seededCoursesArePublishedAndShowProgressState() {
        ApiClient c = verifiedStudent(uniqueEmail());
        var res = c.get("/api/v1/courses");
        assertThat(res.status()).isEqualTo(200);
        String body = res.body();
        assertThat(body).contains("A1 · Fundamentos", "A2 · Elementary");
        assertThat(res.json().get(0).path("modules").get(0).path("lessons").get(0).path("state").asString())
                .isEqualTo("NOT_STARTED");
    }

    @Test
    void lessonDetailIsBilingualAndNeverLeaksSolutions() {
        ApiClient c = verifiedStudent(uniqueEmail());
        var res = c.get("/api/v1/lessons/" + lessonId());
        assertThat(res.status()).isEqualTo(200);
        JsonNode lesson = res.json();
        assertThat(lesson.path("explanation").path("es").asString()).contains("to be");
        assertThat(lesson.path("explanation").path("en").asString()).contains("to be");
        assertThat(lesson.path("exercises").size()).isGreaterThanOrEqualTo(4);
        assertThat(res.body()).doesNotContain("solution", "correctOptionId", "errorIndex", "\"answers\"",
                "\"sentences\"", "\"pairs\"");
    }

    @Test
    void attemptsAreEvaluatedOnTheServerAndRetriesAreIdempotent() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode lesson = c.get("/api/v1/lessons/" + lessonId()).json();
        String runId = c.post("/api/v1/lessons/" + lessonId() + "/runs", null).json().path("runId").asString();
        String exerciseId = lesson.path("exercises").get(0).path("publicId").asString(); // opción múltiple

        String k = key();
        var wrong = attempt(c, exerciseId, runId, Map.of("optionId", "a"), k);
        assertThat(wrong.status()).isEqualTo(200);
        assertThat(wrong.json().path("correct").asBoolean()).isFalse();
        assertThat(wrong.json().path("solution").path("correctOptionId").asString()).isEqualTo("b");
        assertThat(wrong.json().path("feedback").path("es").asString()).isNotBlank();

        // Mismo intento reenviado (p. ej. reintento de red): mismo resultado y no se registra dos veces.
        var retry = attempt(c, exerciseId, runId, Map.of("optionId", "b"), k);
        assertThat(retry.json().path("correct").asBoolean()).isFalse();
        Integer rows = jdbc.queryForObject("select count(*) from exercise_attempts where idempotency_key = ?",
                Integer.class, k);
        assertThat(rows).isEqualTo(1);

        var invalid = attempt(c, exerciseId, runId, Map.of("optionId", "zzz"), key());
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.code()).isEqualTo("INVALID_ANSWER");

        var badKey = c.post("/api/v1/exercises/" + exerciseId + "/attempts",
                Map.of("answer", Map.of("optionId", "b"), "hintsUsed", 0, "idempotencyKey", "short"));
        assertThat(badKey.status()).isEqualTo(400);
    }

    @Test
    void otherStudentsCannotUseMyLessonRun() {
        ApiClient me = verifiedStudent(uniqueEmail());
        ApiClient other = verifiedStudent(uniqueEmail());
        String runId = me.post("/api/v1/lessons/" + lessonId() + "/runs", null).json().path("runId").asString();
        String exerciseId = me.get("/api/v1/lessons/" + lessonId()).json().path("exercises").get(0)
                .path("publicId").asString();

        assertThat(attempt(other, exerciseId, runId, Map.of("optionId", "b"), key()).status()).isEqualTo(404);
        assertThat(other.post("/api/v1/lesson-runs/" + runId + "/complete", null).status()).isEqualTo(404);
    }

    @Test
    void scoreUsesFirstAttemptsAndMasteryNeedsTwoDifferentDays() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode lesson = c.get("/api/v1/lessons/" + lessonId()).json();
        List<String> exerciseIds = lesson.path("exercises").valueStream().map(e -> e.path("publicId").asString())
                .toList();

        // Ejecución 1: primer intento del ejercicio 0 incorrecto y luego corregido; el resto correcto.
        String run1 = c.post("/api/v1/lessons/" + lessonId() + "/runs", null).json().path("runId").asString();
        attempt(c, exerciseIds.get(0), run1, Map.of("optionId", "a"), key());
        attempt(c, exerciseIds.get(0), run1, correctAnswer(exerciseIds.get(0)), key());
        for (String id : exerciseIds.subList(1, exerciseIds.size())) {
            assertThat(attempt(c, id, run1, correctAnswer(id), key()).json().path("correct").asBoolean()).isTrue();
        }
        var done1 = c.post("/api/v1/lesson-runs/" + run1 + "/complete", null);
        assertThat(done1.status()).isEqualTo(200);
        int n = exerciseIds.size();
        assertThat(done1.json().path("correctCount").asInt()).isEqualTo(n - 1);
        assertThat(done1.json().path("score").decimalValue())
                .isEqualByComparingTo(java.math.BigDecimal.valueOf(n - 1).divide(java.math.BigDecimal.valueOf(n),
                        4, java.math.RoundingMode.HALF_UP));
        assertThat(done1.json().path("state").asString()).isEqualTo("PRACTICED");

        // Terminar otra vez la misma ejecución es idempotente.
        var again = c.post("/api/v1/lesson-runs/" + run1 + "/complete", null);
        assertThat(again.json().path("completedRuns").asInt()).isEqualTo(1);
        // Y ya no acepta intentos.
        assertThat(attempt(c, exerciseIds.get(0), run1, correctAnswer(exerciseIds.get(0)), key()).code())
                .isEqualTo("RUN_COMPLETED");

        // Simula que la ejecución 1 fue ayer (los días se cuentan en la zona horaria del alumno).
        jdbc.update("update lesson_runs set local_date = date_sub(local_date, interval 1 day) where public_id = ?",
                run1);

        String run2 = c.post("/api/v1/lessons/" + lessonId() + "/runs", null).json().path("runId").asString();
        for (String id : exerciseIds) {
            attempt(c, id, run2, correctAnswer(id), key());
        }
        var done2 = c.post("/api/v1/lesson-runs/" + run2 + "/complete", null).json();
        assertThat(done2.path("score").decimalValue()).isEqualByComparingTo("1");
        assertThat(done2.path("state").asString()).isEqualTo("MASTERED");
        assertThat(done2.path("newlyMastered").asBoolean()).isTrue();

        var tree = c.get("/api/v1/courses").body();
        assertThat(tree).contains("\"state\":\"MASTERED\"");
        var summary = c.get("/api/v1/me/learning-summary").json();
        assertThat(summary.path("mastered").asInt()).isEqualTo(1);
        assertThat(summary.path("recommended").path("publicId").asString()).isNotEqualTo(lessonId());
    }

    @Test
    void unauthenticatedUsersCannotReadLessons() {
        assertThat(newClient().get("/api/v1/courses").status()).isEqualTo(401);
        assertThat(newClient().get("/api/v1/lessons/" + lessonId()).status()).isEqualTo(401);
    }
}
