package com.englishos.learning.application;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import tools.jackson.databind.JsonNode;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Localized;
import com.englishos.learning.api.Skill;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Course;
import com.englishos.learning.domain.CourseModule;
import com.englishos.learning.domain.Exercise;
import com.englishos.learning.domain.ExerciseEvaluator;
import com.englishos.learning.domain.ExerciseType;
import com.englishos.learning.domain.Lesson;
import com.englishos.learning.infrastructure.CourseModuleRepository;
import com.englishos.learning.infrastructure.CourseRepository;
import com.englishos.learning.infrastructure.ExerciseRepository;
import com.englishos.learning.infrastructure.LessonRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el contenido semilla de {@code classpath:content/seed/*.json} al arrancar. Es idempotente: solo inserta los
 * elementos cuyo {@code slug} no existe, así que nunca sobrescribe ediciones hechas desde el panel.
 *
 * <p>El contenido semilla se publica sin marca de revisión ({@code reviewed_at = NULL}): el panel lo muestra como
 * "sin revisar" hasta que el administrador lo apruebe. Cada ejercicio se valida; un error detiene el arranque.
 */
@Component
@Order(10)
class ContentSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ContentSeeder.class);

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final Json json;
    private final Clock clock;
    private final boolean enabled;

    ContentSeeder(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
            ExerciseRepository exercises, Json json, Clock clock,
            @Value("${app.content.seed-on-startup:true}") boolean enabled) {
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.json = json;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) throws IOException {
        if (!enabled) {
            return;
        }
        Resource[] files = new PathMatchingResourcePatternResolver().getResources("classpath*:content/seed/*.json");
        // Orden determinista por nombre (01-, 02-, ...): los prerrequisitos pueden apuntar a archivos anteriores.
        java.util.Arrays.sort(files, java.util.Comparator.comparing(r -> String.valueOf(r.getFilename())));
        int created = 0;
        for (Resource file : files) {
            try (InputStream in = file.getInputStream()) {
                JsonNode root = json.read(new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8));
                created += seed(root, file.getFilename());
            }
        }
        if (created > 0) {
            log.info("Contenido semilla: {} elementos nuevos (pendientes de revisión humana).", created);
        }
    }

    int seed(JsonNode root, String source) {
        Instant now = Instant.now(clock);
        int created = 0;
        for (JsonNode c : root.path("courses")) {
            Course course = courses.findBySlug(c.path("slug").asString()).orElse(null);
            if (course == null) {
                course = courses.save(Course.create(c.path("slug").asString(), level(c), text(c, "title"),
                        text(c, "description"), ContentStatus.PUBLISHED, c.path("sortOrder").asInt(), now));
                created++;
            }
            int moduleOrder = 0;
            for (JsonNode m : c.path("modules")) {
                moduleOrder++;
                CourseModule module = modules.findBySlug(m.path("slug").asString()).orElse(null);
                if (module == null) {
                    module = modules.save(CourseModule.create(course.getId(), m.path("slug").asString(),
                            text(m, "title"), text(m, "objective"), ContentStatus.PUBLISHED, moduleOrder, now));
                    created++;
                }
                int lessonOrder = 0;
                for (JsonNode l : m.path("lessons")) {
                    lessonOrder++;
                    created += seedLesson(module, l, lessonOrder, source, now);
                }
            }
        }
        int itemOrder = 0;
        for (JsonNode item : root.path("diagnostic")) {
            itemOrder++;
            if (exercises.findBySlug(item.path("slug").asString()).isEmpty()) {
                exercises.save(Exercise.create(item.path("slug").asString(), Exercise.Purpose.DIAGNOSTIC, null,
                        definition(item, level(item), source), ContentStatus.PUBLISHED, itemOrder, now));
                created++;
            }
        }
        return created;
    }

    private int seedLesson(CourseModule module, JsonNode l, int order, String source, Instant now) {
        if (lessons.findBySlug(l.path("slug").asString()).isPresent()) {
            return 0;
        }
        CefrLevel level = level(l);
        Lesson lesson = Lesson.create(module.getId(), l.path("slug").asString(), level, text(l, "title"),
                text(l, "objective"), text(l, "explanation"), json.write(l.path("examples")),
                json.write(l.path("vocabulary")), l.path("minutes").asInt(10), ContentStatus.PUBLISHED, order, now);
        Set<Long> prereqs = new HashSet<>();
        for (JsonNode p : l.path("prerequisites")) {
            lessons.findBySlug(p.asString()).ifPresent(pl -> prereqs.add(pl.getId()));
        }
        lesson.setPrerequisites(prereqs);
        lesson = lessons.save(lesson);
        int created = 1;
        int exOrder = 0;
        for (JsonNode e : l.path("exercises")) {
            exOrder++;
            exercises.save(Exercise.create(e.path("slug").asString(), Exercise.Purpose.LESSON, lesson.getId(),
                    definition(e, level, source), ContentStatus.PUBLISHED, exOrder, now));
            created++;
        }
        return created;
    }

    private Exercise.Definition definition(JsonNode e, CefrLevel level, String source) {
        ExerciseType type = ExerciseType.valueOf(e.path("type").asString());
        List<String> problems = new ArrayList<>(
                ExerciseEvaluator.validateDefinition(type, e.path("prompt"), e.path("solution")));
        if (!problems.isEmpty()) {
            throw new IllegalStateException("Contenido semilla inválido en " + source + " (" + e.path("slug").asString()
                    + "): " + String.join("; ", problems));
        }
        JsonNode hint = e.path("hint");
        return new Exercise.Definition(type, Skill.valueOf(e.path("skill").asString()), level, text(e, "instructions"),
                json.write(e.path("prompt")), json.write(e.path("solution")), text(e, "feedback"),
                hint.isObject() ? new Localized(hint.path("es").asString(), hint.path("en").asString()) : null);
    }

    private static CefrLevel level(JsonNode n) {
        return CefrLevel.valueOf(n.path("level").asString());
    }

    private static Localized text(JsonNode n, String field) {
        JsonNode t = n.path(field);
        return new Localized(t.path("es").asString(), t.path("en").asString());
    }
}
