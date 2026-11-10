package com.englishos.learning;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.englishos.audit.AuditService;
import com.englishos.support.ApiClient;
import com.englishos.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import tools.jackson.databind.JsonNode;

import static org.assertj.core.api.Assertions.assertThat;

/** RF-CRS-3, RF-CRS-4, RF-ADM-2: gestión de contenido por el administrador, con auditoría y estados. */
class ContentAdminIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    AuditService audit;

    private static Map<String, String> text(String es, String en) {
        return Map.of("es", es, "en", en);
    }

    private static Map<String, Object> lessonBody(String slug) {
        Map<String, Object> body = new HashMap<>();
        body.put("slug", slug);
        body.put("level", "A1");
        body.put("title", text("Colores", "Colours"));
        body.put("objective", text("Nombrar colores", "Name colours"));
        body.put("explanation", text("## Colores\nred = rojo", "## Colours\nred"));
        body.put("examplesJson", "[{\"en\":\"The car is red.\",\"es\":\"El coche es rojo.\"}]");
        body.put("vocabularyJson", "[]");
        body.put("estimatedMinutes", 5);
        return body;
    }

    private static Map<String, Object> exerciseBody(String slug, String solutionJson) {
        Map<String, Object> body = new HashMap<>();
        body.put("slug", slug);
        body.put("type", "MULTIPLE_CHOICE");
        body.put("skill", "VOCABULARY");
        body.put("level", "A1");
        body.put("instructions", text("Elige", "Choose"));
        body.put("promptJson",
                "{\"question\":\"rojo = ?\",\"options\":[{\"id\":\"a\",\"text\":\"red\"},{\"id\":\"b\",\"text\":\"blue\"}]}");
        body.put("solutionJson", solutionJson);
        body.put("feedback", text("rojo = red", "rojo = red"));
        return body;
    }

    @Test
    void studentsCannotManageContent() {
        ApiClient student = verifiedStudent(uniqueEmail());
        assertThat(student.get("/api/v1/admin/content/tree").status()).isEqualTo(403);
        String lessonId = jdbc.queryForObject("select public_id from lessons where slug = 'a1-verb-to-be'", String.class);
        assertThat(student.post("/api/v1/admin/content/lessons/" + lessonId + "/status",
                Map.of("status", "ARCHIVED")).status()).isEqualTo(403);
    }

    @Test
    void adminSeesSeededContentAsUnreviewed() {
        JsonNode tree = admin().get("/api/v1/admin/content/tree").json();
        assertThat(tree.size()).isGreaterThanOrEqualTo(2);
        JsonNode firstLesson = tree.get(0).path("modules").get(0).path("lessons").get(0);
        assertThat(firstLesson.path("status").asString()).isEqualTo("PUBLISHED");
        assertThat(firstLesson.has("reviewed")).isTrue();
    }

    @Test
    void draftLessonLifecycleWithValidationAndAudit() {
        ApiClient admin = admin();
        ApiClient student = verifiedStudent(uniqueEmail());
        String moduleId = jdbc.queryForObject(
                "select public_id from course_modules where slug = 'a1-m1-be-and-articles'", String.class);
        String slug = "it-colours-" + UUID.randomUUID().toString().substring(0, 8);

        var created = admin.post("/api/v1/admin/content/modules/" + moduleId + "/lessons", lessonBody(slug));
        assertThat(created.status()).as(created.body()).isEqualTo(201);
        String lessonId = created.json().path("publicId").asString();
        assertThat(created.json().path("status").asString()).isEqualTo("DRAFT");

        // Un borrador no es visible para los alumnos.
        assertThat(student.get("/api/v1/lessons/" + lessonId).status()).isEqualTo(404);
        // No se puede publicar sin ejercicios publicados.
        assertThat(admin.post("/api/v1/admin/content/lessons/" + lessonId + "/status", Map.of("status", "PUBLISHED"))
                .code()).isEqualTo("LESSON_WITHOUT_EXERCISES");

        // Definición inválida (solución apunta a una opción inexistente).
        var invalid = admin.post("/api/v1/admin/content/lessons/" + lessonId + "/exercises",
                exerciseBody(slug + "-ex", "{\"correctOptionId\":\"z\"}"));
        assertThat(invalid.status()).isEqualTo(400);
        assertThat(invalid.code()).isEqualTo("INVALID_EXERCISE");

        var ex = admin.post("/api/v1/admin/content/lessons/" + lessonId + "/exercises",
                exerciseBody(slug + "-ex", "{\"correctOptionId\":\"a\"}"));
        assertThat(ex.status()).as(ex.body()).isEqualTo(201);
        String exerciseId = ex.json().path("publicId").asString();
        assertThat(admin.post("/api/v1/admin/content/exercises/" + exerciseId + "/status",
                Map.of("status", "PUBLISHED")).status()).isEqualTo(200);
        assertThat(admin.post("/api/v1/admin/content/lessons/" + lessonId + "/status",
                Map.of("status", "PUBLISHED")).status()).isEqualTo(200);

        var visible = student.get("/api/v1/lessons/" + lessonId);
        assertThat(visible.status()).isEqualTo(200);
        assertThat(visible.body()).doesNotContain("correctOptionId");

        // Editar marca la lección como pendiente de revisión y sube la versión de contenido.
        assertThat(admin.post("/api/v1/admin/content/lessons/" + lessonId + "/review", null).json()
                .path("reviewed").asBoolean()).isTrue();
        var edited = admin.put("/api/v1/admin/content/lessons/" + lessonId, lessonBody(slug));
        assertThat(edited.json().path("reviewed").asBoolean()).isFalse();
        assertThat(edited.json().path("contentVersion").asInt()).isEqualTo(2);

        assertThat(audit.findByTarget("LESSON", lessonId)).extracting(AuditService.AuditEntry::action)
                .contains("LESSON_CREATED", "LESSON_STATUS_CHANGED", "LESSON_REVIEWED", "LESSON_UPDATED");

        // Archivar la oculta de nuevo.
        admin.post("/api/v1/admin/content/lessons/" + lessonId + "/status", Map.of("status", "ARCHIVED"));
        assertThat(student.get("/api/v1/lessons/" + lessonId).status()).isEqualTo(404);
    }

    @Test
    void invalidJsonAndSlugsAreRejected() {
        ApiClient admin = admin();
        String moduleId = jdbc.queryForObject(
                "select public_id from course_modules where slug = 'a1-m1-be-and-articles'", String.class);
        Map<String, Object> badSlug = lessonBody("Bad Slug!");
        assertThat(admin.post("/api/v1/admin/content/modules/" + moduleId + "/lessons", badSlug).code())
                .isEqualTo("INVALID_SLUG");
        Map<String, Object> badJson = lessonBody("it-bad-json-" + UUID.randomUUID().toString().substring(0, 8));
        badJson.put("examplesJson", "{not json");
        assertThat(admin.post("/api/v1/admin/content/modules/" + moduleId + "/lessons", badJson).code())
                .isEqualTo("INVALID_JSON");
    }
}
