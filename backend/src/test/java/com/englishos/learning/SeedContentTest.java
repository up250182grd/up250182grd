package com.englishos.learning;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Skill;
import com.englishos.learning.domain.ExerciseEvaluator;
import com.englishos.learning.domain.ExerciseType;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/** Calidad estructural del contenido semilla: definiciones válidas, slugs únicos, bilingüe y banco suficiente. */
class SeedContentTest {

    private static final List<JsonNode> exercises = new ArrayList<>();
    private static final List<JsonNode> lessons = new ArrayList<>();
    private static final List<JsonNode> diagnostic = new ArrayList<>();

    @BeforeAll
    static void load() throws IOException {
        JsonMapper mapper = JsonMapper.builder().build();
        for (Resource r : new PathMatchingResourcePatternResolver().getResources("classpath*:content/seed/*.json")) {
            JsonNode root = mapper.readTree(r.getInputStream());
            root.path("courses").forEach(c -> c.path("modules").forEach(m -> m.path("lessons").forEach(l -> {
                lessons.add(l);
                l.path("exercises").forEach(exercises::add);
            })));
            root.path("diagnostic").forEach(diagnostic::add);
        }
    }

    @Test
    void everyExerciseDefinitionIsValid() {
        List<JsonNode> all = new ArrayList<>(exercises);
        all.addAll(diagnostic);
        assertThat(all).isNotEmpty();
        for (JsonNode e : all) {
            ExerciseType type = ExerciseType.valueOf(e.path("type").asString());
            assertThat(ExerciseEvaluator.validateDefinition(type, e.path("prompt"), e.path("solution")))
                    .as(e.path("slug").asString()).isEmpty();
            Skill.valueOf(e.path("skill").asString());
        }
    }

    @Test
    void slugsAreUnique() {
        Set<String> seen = new HashSet<>();
        for (JsonNode n : concat()) {
            assertThat(seen.add(n.path("slug").asString())).as("slug duplicado " + n.path("slug")).isTrue();
        }
    }

    @Test
    void pedagogicalTextsExistInSpanishAndEnglish() {
        for (JsonNode l : lessons) {
            for (String field : List.of("title", "objective", "explanation")) {
                assertThat(l.path(field).path("es").asString()).as(l.path("slug") + "." + field + ".es").isNotBlank();
                assertThat(l.path(field).path("en").asString()).as(l.path("slug") + "." + field + ".en").isNotBlank();
            }
        }
        for (JsonNode e : concatExercises()) {
            for (String field : List.of("instructions", "feedback")) {
                assertThat(e.path(field).path("es").asString()).as(e.path("slug") + "." + field).isNotBlank();
                assertThat(e.path(field).path("en").asString()).as(e.path("slug") + "." + field).isNotBlank();
            }
        }
    }

    @Test
    void everyLessonHasEnoughExercises() {
        assertThat(lessons).allSatisfy(l -> assertThat(l.path("exercises").size()).isGreaterThanOrEqualTo(4));
    }

    /** El diagnóstico necesita ≥ 3 ítems por habilidad y nivel en el rango A1–B2 que cubre la escalera. */
    @Test
    void diagnosticBankCoversEverySkillAndLevel() {
        for (Skill skill : Skill.values()) {
            for (CefrLevel level : List.of(CefrLevel.A1, CefrLevel.A2, CefrLevel.B1, CefrLevel.B2)) {
                long count = diagnostic.stream()
                        .filter(d -> d.path("skill").asString().equals(skill.name())
                                && d.path("level").asString().equals(level.name()))
                        .count();
                assertThat(count).as(skill + " " + level).isGreaterThanOrEqualTo(3);
            }
        }
    }

    private static List<JsonNode> concatExercises() {
        List<JsonNode> all = new ArrayList<>(exercises);
        all.addAll(diagnostic);
        return all;
    }

    private static List<JsonNode> concat() {
        List<JsonNode> all = new ArrayList<>(lessons);
        all.addAll(concatExercises());
        return all;
    }
}
