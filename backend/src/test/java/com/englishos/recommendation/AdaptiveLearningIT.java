package com.englishos.recommendation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.ZoneId;
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

/**
 * Fase 6 de punta a punta contra MySQL real: vocabulario de las lecciones → tarjetas SM-2, cola diaria y repasos
 * idempotentes; errores del profesor → práctica; recomendaciones por reglas con pesos versionados; progreso.
 */
class AdaptiveLearningIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String LESSON_SLUG = "a1-verb-to-be";
    private static final List<String> LESSON_TERMS = List.of("friend", "happy", "student", "teacher");

    @Autowired
    JdbcTemplate jdbc;

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private Map<String, Object> correctAnswer(String exercisePublicId) {
        Map<String, Object> row = jdbc.queryForMap(
                "select type, solution from exercises where public_id = ?", exercisePublicId);
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

    /** Completa una ejecución de la lección respondiendo todo bien. */
    private void completeLesson(ApiClient c) {
        String lessonId = jdbc.queryForObject("select public_id from lessons where slug = ?", String.class, LESSON_SLUG);
        List<String> exercises = c.get("/api/v1/lessons/" + lessonId).json().path("exercises").valueStream()
                .map(e -> e.path("publicId").asString()).toList();
        String run = c.post("/api/v1/lessons/" + lessonId + "/runs", null).json().path("runId").asString();
        for (String id : exercises) {
            c.post("/api/v1/exercises/" + id + "/attempts", Map.of("runId", run, "answer", correctAnswer(id),
                    "hintsUsed", 0, "durationMs", 4000, "idempotencyKey", key()));
        }
        assertThat(c.post("/api/v1/lesson-runs/" + run + "/complete", null).status()).isEqualTo(200);
    }

    private List<String> recommendationTypes(ApiClient c) {
        return c.get("/api/v1/me/recommendations").json().path("items").valueStream()
                .map(i -> i.path("type").asString()).toList();
    }

    // ---- Vocabulario y SM-2 -------------------------------------------------------------------

    @Test
    void lessonVocabularyBecomesCardsReviewedWithSm2() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode empty = c.get("/api/v1/vocabulary/queue").json();
        assertThat(empty.path("items").size()).isZero();

        completeLesson(c);
        JsonNode queue = c.get("/api/v1/vocabulary/queue").json();
        List<String> terms = queue.path("items").valueStream().map(i -> i.path("card").path("term").asString())
                .sorted().toList();
        assertThat(terms).containsAll(LESSON_TERMS);
        assertThat(queue.path("newCount").asInt()).isEqualTo(terms.size());
        assertThat(queue.path("dueCount").asInt()).isZero();
        JsonNode first = queue.path("items").get(0);
        // El servidor calcula el intervalo que daría cada botón (primera vez: olvido 1, resto 1 día).
        assertThat(first.path("previews").valueStream().map(p -> p.path("intervalDays").asInt()).toList())
                .containsExactly(1, 1, 1, 1);
        assertThat(first.path("card").path("translation").asString()).isNotBlank();

        String cardId = first.path("card").path("id").asString();
        String reviewId = key();
        JsonNode r = c.post("/api/v1/vocabulary/cards/" + cardId + "/reviews",
                Map.of("quality", 4, "reviewId", reviewId, "durationMs", 3000)).json();
        assertThat(r.path("replayed").asBoolean()).isFalse();
        assertThat(r.path("card").path("state").asString()).isEqualTo("LEARNING");
        assertThat(r.path("card").path("intervalDays").asInt()).isEqualTo(1);
        assertThat(r.path("card").path("easeFactor").decimalValue()).isEqualByComparingTo("2.500");
        // Vence al inicio del día siguiente en la zona del alumno (por defecto, Ciudad de México).
        ZoneId mx = ZoneId.of("America/Mexico_City");
        assertThat(r.path("card").path("dueAt").asString())
                .isEqualTo(LocalDate.now(mx).plusDays(1).atStartOfDay(mx).toInstant().toString());

        // Reintento con el mismo identificador: no aplica SM-2 dos veces.
        JsonNode replay = c.post("/api/v1/vocabulary/cards/" + cardId + "/reviews",
                Map.of("quality", 5, "reviewId", reviewId)).json();
        assertThat(replay.path("replayed").asBoolean()).isTrue();
        assertThat(replay.path("quality").asInt()).isEqualTo(4);
        assertThat(jdbc.queryForObject("""
                select count(*) from vocabulary_reviews r join user_vocabulary_cards c on c.id = r.card_id
                where c.public_id = ?""", Integer.class, cardId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select review_count from user_vocabulary_cards where public_id = ?",
                Integer.class, cardId)).isEqualTo(1);

        // Ya no está en la cola de hoy, y el historial guarda el estado antes/después.
        JsonNode after = c.get("/api/v1/vocabulary/queue").json();
        assertThat(after.path("items").valueStream().map(i -> i.path("card").path("id").asString()))
                .doesNotContain(cardId);
        assertThat(jdbc.queryForObject("""
                select json_unquote(json_extract(state_after, '$.algorithm')) from vocabulary_reviews r
                join user_vocabulary_cards c on c.id = r.card_id where c.public_id = ?""", String.class, cardId))
                .isEqualTo("SM2");

        // "Mis palabras" lista todas, ordenadas.
        List<String> words = c.get("/api/v1/vocabulary/cards").json().valueStream()
                .map(w -> w.path("term").asString()).toList();
        assertThat(words).containsAll(LESSON_TERMS).isSorted();
    }

    @Test
    void reviewsAreValidatedAndOwned() {
        ApiClient me = verifiedStudent(uniqueEmail());
        completeLesson(me);
        String cardId = me.get("/api/v1/vocabulary/queue").json().path("items").get(0).path("card").path("id")
                .asString();
        assertThat(me.post("/api/v1/vocabulary/cards/" + cardId + "/reviews",
                Map.of("quality", 7, "reviewId", key())).status()).isEqualTo(400);
        assertThat(me.post("/api/v1/vocabulary/cards/" + cardId + "/reviews",
                Map.of("quality", 3)).status()).isEqualTo(400);

        ApiClient other = verifiedStudent(uniqueEmail());
        var res = other.post("/api/v1/vocabulary/cards/" + cardId + "/reviews", Map.of("quality", 5, "reviewId", key()));
        assertThat(res.status()).isEqualTo(404);
        assertThat(res.code()).isEqualTo("CARD_NOT_FOUND");
    }

    // ---- Errores recurrentes → práctica -------------------------------------------------------

    @Test
    void tutorCorrectionsBecomePracticeUntilResolved() {
        ApiClient c = verifiedStudent(uniqueEmail());
        c.post("/api/v1/me/wallet/simulated-topups", Map.of("amount", new BigDecimal("100.00"), "idempotencyKey", key()));
        String conv = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).json().path("id").asString();
        c.postSse("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "Hello! She have a cat and I is happy."));

        JsonNode queue = c.get("/api/v1/practice/errors").json();
        assertThat(queue.size()).isEqualTo(2);
        JsonNode item = queue.get(0);
        assertThat(item.has("correction")).as("la respuesta no se envía antes de contestar").isFalse();
        assertThat(item.path("categoryOpenCount").asInt()).isEqualTo(2);
        String id = item.path("id").asString();
        String expected = jdbc.queryForObject("select correction from learner_errors where public_id = ?",
                String.class, id);

        JsonNode wrong = c.post("/api/v1/practice/errors/" + id + "/answer", Map.of("answer", item.path("original").asString())).json();
        assertThat(wrong.path("correct").asBoolean()).isFalse();
        assertThat(wrong.path("expected").asString()).isEqualTo(expected);

        JsonNode ok1 = c.post("/api/v1/practice/errors/" + id + "/answer", Map.of("answer", "  " + expected.toUpperCase() + ". ")).json();
        assertThat(ok1.path("correct").asBoolean()).as("ignora mayúsculas, espacios y punto final").isTrue();
        assertThat(ok1.path("resolved").asBoolean()).isFalse();
        JsonNode ok2 = c.post("/api/v1/practice/errors/" + id + "/answer", Map.of("answer", expected)).json();
        assertThat(ok2.path("resolved").asBoolean()).isTrue();

        assertThat(c.get("/api/v1/practice/errors").json().valueStream().map(e -> e.path("id").asString()))
                .doesNotContain(id);
        assertThat(c.post("/api/v1/practice/errors/" + id + "/answer", Map.of("answer", expected)).code())
                .isEqualTo("ERROR_ALREADY_RESOLVED");
        ApiClient other = verifiedStudent(uniqueEmail());
        assertThat(other.post("/api/v1/practice/errors/" + queue.get(1).path("id").asString() + "/answer",
                Map.of("answer", "x")).status()).isEqualTo(404);
    }

    // ---- Recomendaciones y progreso -----------------------------------------------------------

    @Test
    void recommendationsFollowTheStudentsRealState() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode first = c.get("/api/v1/me/recommendations").json();
        assertThat(first.path("items").get(0).path("type").asString()).isEqualTo("DIAGNOSTIC");
        assertThat(first.path("dailyGoalMinutes").asInt()).isEqualTo(20);
        assertThat(recommendationTypes(c)).doesNotContain("REVIEW_CARDS", "CONVERSATION"); // sin palabras ni saldo

        completeLesson(c);
        assertThat(recommendationTypes(c)).contains("REVIEW_CARDS");
        c.post("/api/v1/me/wallet/simulated-topups", Map.of("amount", new BigDecimal("100.00"), "idempotencyKey", key()));
        assertThat(recommendationTypes(c)).contains("CONVERSATION");

        JsonNode progress = c.get("/api/v1/me/progress").json();
        assertThat(progress.path("activity").size()).isEqualTo(14);
        JsonNode today = progress.path("activity").get(13);
        assertThat(today.path("date").asString()).isEqualTo(LocalDate.now(ZoneId.of("America/Mexico_City")).toString());
        assertThat(today.path("exercises").asInt()).isPositive();
        // Estimación: 4 s por ejercicio (la duración informada), redondeada al minuto.
        assertThat(today.path("minutes").asInt()).isEqualTo((today.path("exercises").asInt() * 4000 + 30_000) / 60_000);
        assertThat(progress.path("activeDaysLast14").asInt()).isEqualTo(1);
        assertThat(progress.path("vocabulary").path("total").asInt()).isGreaterThanOrEqualTo(LESSON_TERMS.size());
    }

    @Test
    void weightsAreAdminOnlyValidatedVersionedAndAudited() {
        ApiClient student = verifiedStudent(uniqueEmail());
        Map<String, Integer> valid = Map.of("diagnostic", 100, "reviews", 80, "nextLesson", 70,
                "recurringErrors", 60, "weakSkill", 45, "conversation", 35);
        assertThat(student.get("/api/v1/admin/recommendations/weights").status()).isEqualTo(403);
        assertThat(student.post("/api/v1/admin/recommendations/weights",
                Map.of("weights", valid, "note", "intento de alumno")).status()).isEqualTo(403);

        ApiClient admin = admin();
        JsonNode history = admin.get("/api/v1/admin/recommendations/weights").json();
        assertThat(history.get(0).path("weights").path("reviews").asInt()).isBetween(0, 100);

        var tooHigh = new java.util.HashMap<>(valid);
        tooHigh.put("reviews", 150);
        assertThat(admin.post("/api/v1/admin/recommendations/weights", Map.of("weights", tooHigh,
                "note", "Prueba de validación")).code()).isEqualTo("INVALID_WEIGHTS");
        var unknown = new java.util.HashMap<>(valid);
        unknown.put("magic", 10);
        assertThat(admin.post("/api/v1/admin/recommendations/weights", Map.of("weights", unknown,
                "note", "Prueba de validación")).code()).isEqualTo("INVALID_WEIGHTS");
        assertThat(admin.post("/api/v1/admin/recommendations/weights", Map.of("weights", valid,
                "note", "  ")).status()).isEqualTo(400);

        // Desactivar el diagnóstico: un alumno nuevo ya no lo ve recomendado.
        var noDiagnostic = new java.util.HashMap<>(valid);
        noDiagnostic.put("diagnostic", 0);
        try {
            var created = admin.post("/api/v1/admin/recommendations/weights", Map.of("weights", noDiagnostic,
                    "note", "Prueba de integración: sin diagnóstico"));
            assertThat(created.status()).isEqualTo(201);
            String versionId = created.json().path("id").asString();
            assertThat(recommendationTypes(verifiedStudent(uniqueEmail()))).doesNotContain("DIAGNOSTIC");
            assertThat(jdbc.queryForObject("""
                    select count(*) from audit_logs where action = 'RECOMMENDATION_WEIGHTS_UPDATED' and target_id = ?""",
                    Integer.class, versionId)).isEqualTo(1);
        } finally {
            admin.post("/api/v1/admin/recommendations/weights", Map.of("weights", valid,
                    "note", "Prueba de integración: restaurar valores"));
        }
    }
}
