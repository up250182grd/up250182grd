package com.englishos.gamification;

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

/** Fase 8 de punta a punta: XP por actividades completadas, racha con descanso e insignias, a partir de eventos. */
class GamificationIT extends IntegrationTest {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final ZoneId MX = ZoneId.of("America/Mexico_City");

    @Autowired
    JdbcTemplate jdbc;

    private static String key() {
        return UUID.randomUUID().toString();
    }

    private Map<String, Object> correctAnswer(String exercisePublicId) {
        Map<String, Object> row = jdbc.queryForMap("select type, solution from exercises where public_id = ?",
                exercisePublicId);
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

    private void completeLesson(ApiClient c) {
        String lessonId = jdbc.queryForObject("select public_id from lessons where slug = 'a1-verb-to-be'", String.class);
        List<String> exercises = c.get("/api/v1/lessons/" + lessonId).json().path("exercises").valueStream()
                .map(e -> e.path("publicId").asString()).toList();
        String run = c.post("/api/v1/lessons/" + lessonId + "/runs", null).json().path("runId").asString();
        for (String id : exercises) {
            c.post("/api/v1/exercises/" + id + "/attempts", Map.of("runId", run, "answer", correctAnswer(id),
                    "hintsUsed", 0, "durationMs", 2000, "idempotencyKey", key()));
        }
        assertThat(c.post("/api/v1/lesson-runs/" + run + "/complete", null).status()).isEqualTo(200);
    }

    private JsonNode achievements(ApiClient c) {
        return c.get("/api/v1/me/achievements").json();
    }

    private boolean earned(JsonNode a, String code) {
        return a.path("badges").valueStream().anyMatch(b -> code.equals(b.path("code").asString())
                && b.path("earned").asBoolean());
    }

    private Long userId(String email) {
        return jdbc.queryForObject("select id from users where email = ?", Long.class, email);
    }

    @Test
    void completedActivitiesGiveXpRepeatsGiveLessAndRetriesGiveNothing() {
        ApiClient c = verifiedStudent(uniqueEmail());
        JsonNode empty = achievements(c);
        assertThat(empty.path("xpTotal").asLong()).isZero();
        assertThat(empty.path("streak").path("current").asInt()).isZero();
        assertThat(empty.path("badgesEarned").asInt()).isZero();

        // Responder ejercicios no da XP por sí solo; terminar la lección sí (20) y cuenta para la racha.
        completeLesson(c);
        JsonNode a = achievements(c);
        assertThat(a.path("xpTotal").asLong()).isEqualTo(20);
        assertThat(a.path("xpToday").asInt()).isEqualTo(20);
        assertThat(a.path("streak").path("current").asInt()).isEqualTo(1);
        assertThat(a.path("streak").path("today").path("qualified").asBoolean()).isTrue();
        assertThat(a.path("streak").path("today").path("lessons").asInt()).isEqualTo(1);
        assertThat(earned(a, "FIRST_LESSON")).isTrue();
        assertThat(a.path("streak").path("last28Days").size()).isEqualTo(28);
        assertThat(a.path("streak").path("last28Days").get(27).path("status").asString()).isEqualTo("QUALIFIED");

        // Repetir la misma lección el mismo día: 10 y luego 5.
        completeLesson(c);
        completeLesson(c);
        assertThat(achievements(c).path("xpTotal").asLong()).isEqualTo(35);

        // Repaso: +2; el reintento con el mismo id no suma.
        String card = c.get("/api/v1/vocabulary/queue").json().path("items").get(0).path("card").path("id").asString();
        String reviewId = key();
        c.post("/api/v1/vocabulary/cards/" + card + "/reviews", Map.of("quality", 4, "reviewId", reviewId));
        c.post("/api/v1/vocabulary/cards/" + card + "/reviews", Map.of("quality", 4, "reviewId", reviewId));
        JsonNode after = achievements(c);
        assertThat(after.path("xpTotal").asLong()).isEqualTo(37);
        assertThat(after.path("recentXp").get(0).path("source").asString()).isEqualTo("REVIEW");
    }

    @Test
    void tutorConversationsAndResolvedErrorsCount() {
        ApiClient c = verifiedStudent(uniqueEmail());
        c.post("/api/v1/me/wallet/simulated-topups", Map.of("amount", new BigDecimal("100.00"), "idempotencyKey", key()));
        String conv = c.post("/api/v1/tutor/conversations", Map.of("mode", "FREE")).json().path("id").asString();
        c.postSse("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "Hello! She have a cat."));
        c.postSse("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "I like my cat very much."));
        c.postSse("/api/v1/tutor/conversations/" + conv + "/messages", Map.of("text", "We play in the garden."));
        c.post("/api/v1/tutor/conversations/" + conv + "/close", null);

        JsonNode a = achievements(c);
        assertThat(a.path("xpTotal").asLong()).isEqualTo(15);
        assertThat(earned(a, "FIRST_CONVERSATION")).isTrue();
        // 3 mensajes ≈ 3 minutos: aún no es un día significativo (≥ 5 min).
        assertThat(a.path("streak").path("today").path("minutes").asInt()).isEqualTo(3);
        assertThat(a.path("streak").path("today").path("qualified").asBoolean()).isFalse();

        JsonNode err = c.get("/api/v1/practice/errors").json().get(0);
        String expected = jdbc.queryForObject("select correction from learner_errors where public_id = ?", String.class,
                err.path("id").asString());
        c.post("/api/v1/practice/errors/" + err.path("id").asString() + "/answer", Map.of("answer", expected));
        c.post("/api/v1/practice/errors/" + err.path("id").asString() + "/answer", Map.of("answer", expected));
        assertThat(achievements(c).path("xpTotal").asLong()).isEqualTo(25);
    }

    @Test
    void streakToleratesOneRestDayPerWeekAndAwardsTheSevenDayBadge() {
        String email = uniqueEmail();
        ApiClient c = verifiedStudent(email);
        Long uid = userId(email);
        LocalDate today = LocalDate.now(MX);
        // Historia: 7 días seguidos que terminan ayer (actividad significativa: 10 repasos).
        for (int i = 1; i <= 7; i++) {
            jdbc.update("insert into daily_activity (user_id, local_date, activity_ms, lessons, reviews) values (?, ?, 0, 0, 10)",
                    uid, java.sql.Date.valueOf(today.minusDays(i)));
        }
        JsonNode a = achievements(c);
        assertThat(a.path("streak").path("current").asInt()).as("hoy pendiente no rompe").isEqualTo(7);
        assertThat(a.path("streak").path("today").path("qualified").asBoolean()).isFalse();
        // La insignia se otorga con el siguiente hecho registrado.
        completeLesson(c);
        a = achievements(c);
        assertThat(a.path("streak").path("current").asInt()).isEqualTo(8);
        assertThat(earned(a, "STREAK_7")).isTrue();

        // Un hueco en la semana se tolera como descanso (no suma); un segundo hueco la misma semana la rompe.
        String email2 = uniqueEmail();
        ApiClient d = verifiedStudent(email2);
        Long uid2 = userId(email2);
        LocalDate monday = today.with(java.time.DayOfWeek.MONDAY).minusWeeks(1); // semana anterior completa
        for (LocalDate x : List.of(monday, monday.plusDays(1), monday.plusDays(3), monday.plusDays(4))) {
            jdbc.update("insert into daily_activity (user_id, local_date, activity_ms, lessons, reviews) values (?, ?, 0, 1, 0)",
                    uid2, java.sql.Date.valueOf(x));
        }
        JsonNode s = achievements(d).path("streak");
        assertThat(s.path("best").asInt()).as("lun, mar, (mié descanso), jue, vie").isEqualTo(4);
        assertThat(s.path("last28Days").valueStream()
                .filter(x -> x.path("date").asString().equals(monday.plusDays(2).toString()))
                .findFirst().orElseThrow().path("status").asString()).isEqualTo("REST");
    }
}
