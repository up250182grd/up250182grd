package com.englishos.learning.application;

import java.time.Clock;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;

import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
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
import com.englishos.shared.error.ApiException;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Gestión del contenido educativo por el administrador (RF-CRS-4, RF-ADM-2). Toda modificación queda auditada.
 * Editar una lección la marca como "pendiente de revisión" hasta que una persona la revise.
 */
@Service
public class ContentAdminService {

    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(-[a-z0-9]+)*");

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final AuditService audit;
    private final Json json;
    private final Clock clock;

    ContentAdminService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
            ExerciseRepository exercises, AuditService audit, Json json, Clock clock) {
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record AdminLessonNode(String publicId, String slug, CefrLevel level, Localized title, String status,
            boolean reviewed, int exerciseCount, int sortOrder) {
    }

    public record AdminModuleNode(String publicId, String slug, Localized title, String status,
            List<AdminLessonNode> lessons) {
    }

    public record AdminCourseNode(String publicId, String slug, CefrLevel level, Localized title, String status,
            List<AdminModuleNode> modules) {
    }

    public record AdminExercise(String publicId, String slug, ExerciseType type, Skill skill, CefrLevel level,
            Localized instructions, JsonNode prompt, JsonNode solution, Localized feedback, Localized hint,
            String status, int sortOrder) {
    }

    public record AdminLesson(String publicId, String slug, CefrLevel level, Localized title, Localized objective,
            Localized explanation, JsonNode examples, JsonNode vocabulary, int estimatedMinutes, String status,
            boolean reviewed, int contentVersion, List<AdminExercise> exercises) {
    }

    public record LessonInput(String slug, CefrLevel level, Localized title, Localized objective,
            Localized explanation, String examplesJson, String vocabularyJson, int estimatedMinutes) {
    }

    public record ExerciseInput(String slug, ExerciseType type, Skill skill, CefrLevel level, Localized instructions,
            String promptJson, String solutionJson, Localized feedback, Localized hint) {
    }

    public enum Direction { UP, DOWN }

    // ---- Lectura ------------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('content:read:any')")
    @Transactional(readOnly = true)
    public List<AdminCourseNode> tree() {
        List<Course> cs = courses.findAllByOrderByLevelAscSortOrderAsc();
        List<CourseModule> ms = cs.isEmpty() ? List.of()
                : modules.findByCourseIdInOrderBySortOrderAsc(cs.stream().map(Course::getId).toList());
        List<Lesson> ls = ms.isEmpty() ? List.of()
                : lessons.findByModuleIdInOrderBySortOrderAsc(ms.stream().map(CourseModule::getId).toList());
        Map<Long, Integer> counts = new HashMap<>();
        if (!ls.isEmpty()) {
            for (Object[] row : exercises.countByLessonIds(ls.stream().map(Lesson::getId).toList())) {
                counts.put((Long) row[0], ((Long) row[1]).intValue());
            }
        }
        return cs.stream().map(c -> new AdminCourseNode(c.getPublicId(), c.getSlug(), c.getLevel(), c.getTitle(),
                c.getStatus().name(), ms.stream().filter(m -> m.getCourseId().equals(c.getId()))
                        .map(m -> new AdminModuleNode(m.getPublicId(), m.getSlug(), m.getTitle(), m.getStatus().name(),
                                ls.stream().filter(l -> l.getModuleId().equals(m.getId()))
                                        .map(l -> new AdminLessonNode(l.getPublicId(), l.getSlug(), l.getLevel(),
                                                l.getTitle(), l.getStatus().name(), l.getReviewedAt() != null,
                                                counts.getOrDefault(l.getId(), 0), l.getSortOrder()))
                                        .toList()))
                        .toList()))
                .toList();
    }

    @PreAuthorize("hasAuthority('content:read:any')")
    @Transactional(readOnly = true)
    public AdminLesson lesson(String publicId) {
        return toAdminLesson(findLesson(publicId));
    }

    // ---- Lecciones ----------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminLesson createLesson(CurrentUser actor, String modulePublicId, LessonInput in) {
        CourseModule module = modules.findByPublicId(modulePublicId)
                .orElseThrow(() -> ApiException.notFound("MODULE_NOT_FOUND", "Módulo no encontrado."));
        validateSlug(in.slug());
        if (lessons.findBySlug(in.slug()).isPresent()) {
            throw ApiException.conflict("SLUG_TAKEN", "Ya existe una lección con ese identificador.");
        }
        Instant now = Instant.now(clock);
        Lesson lesson = lessons.save(Lesson.create(module.getId(), in.slug(), in.level(), in.title(), in.objective(),
                in.explanation(), examples(in.examplesJson()), vocabulary(in.vocabularyJson()), in.estimatedMinutes(),
                ContentStatus.DRAFT, lessons.maxSortOrder(module.getId()) + 1, now));
        audit.record(actor.id(), "LESSON_CREATED", "LESSON", lesson.getPublicId(), null,
                Map.of("slug", lesson.getSlug(), "module", module.getSlug()));
        return toAdminLesson(lesson);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminLesson updateLesson(CurrentUser actor, String publicId, LessonInput in) {
        Lesson lesson = findLesson(publicId);
        int before = lesson.getContentVersion();
        lesson.edit(in.title(), in.objective(), in.explanation(), examples(in.examplesJson()),
                vocabulary(in.vocabularyJson()), in.estimatedMinutes(), Instant.now(clock));
        audit.record(actor.id(), "LESSON_UPDATED", "LESSON", lesson.getPublicId(),
                Map.of("contentVersion", before), Map.of("contentVersion", lesson.getContentVersion()));
        return toAdminLesson(lesson);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminLesson changeLessonStatus(CurrentUser actor, String publicId, ContentStatus status) {
        Lesson lesson = findLesson(publicId);
        if (status == ContentStatus.PUBLISHED && exercises
                .findByLessonIdAndStatusOrderBySortOrderAsc(lesson.getId(), ContentStatus.PUBLISHED).isEmpty()) {
            throw ApiException.badRequest("LESSON_WITHOUT_EXERCISES",
                    "Publica al menos un ejercicio antes de publicar la lección.");
        }
        String before = lesson.getStatus().name();
        lesson.changeStatus(status, Instant.now(clock));
        audit.record(actor.id(), "LESSON_STATUS_CHANGED", "LESSON", lesson.getPublicId(),
                Map.of("status", before), Map.of("status", status.name()));
        return toAdminLesson(lesson);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminLesson markReviewed(CurrentUser actor, String publicId) {
        Lesson lesson = findLesson(publicId);
        lesson.markReviewed(Instant.now(clock));
        audit.record(actor.id(), "LESSON_REVIEWED", "LESSON", lesson.getPublicId(), null,
                Map.of("contentVersion", lesson.getContentVersion()));
        return toAdminLesson(lesson);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public void moveLesson(CurrentUser actor, String publicId, Direction direction) {
        Lesson lesson = findLesson(publicId);
        List<Lesson> siblings = lessons.findByModuleIdOrderBySortOrderAsc(lesson.getModuleId());
        int idx = siblings.indexOf(lesson);
        int target = direction == Direction.UP ? idx - 1 : idx + 1;
        if (target < 0 || target >= siblings.size()) {
            return;
        }
        Lesson other = siblings.get(target);
        Instant now = Instant.now(clock);
        int a = lesson.getSortOrder();
        lesson.moveTo(other.getSortOrder(), now);
        other.moveTo(a, now);
        audit.record(actor.id(), "LESSON_MOVED", "LESSON", lesson.getPublicId(), Map.of("sortOrder", a),
                Map.of("sortOrder", lesson.getSortOrder()));
    }

    // ---- Ejercicios ---------------------------------------------------------------------------

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminExercise createExercise(CurrentUser actor, String lessonPublicId, ExerciseInput in) {
        Lesson lesson = findLesson(lessonPublicId);
        validateSlug(in.slug());
        if (exercises.findBySlug(in.slug()).isPresent()) {
            throw ApiException.conflict("SLUG_TAKEN", "Ya existe un ejercicio con ese identificador.");
        }
        Exercise.Definition def = definition(in);
        Exercise e = exercises.save(Exercise.create(in.slug(), Exercise.Purpose.LESSON, lesson.getId(), def,
                ContentStatus.DRAFT, exercises.maxSortOrder(lesson.getId()) + 1, Instant.now(clock)));
        audit.record(actor.id(), "EXERCISE_CREATED", "EXERCISE", e.getPublicId(), null,
                Map.of("slug", e.getSlug(), "lesson", lesson.getSlug()));
        return toAdminExercise(e);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminExercise updateExercise(CurrentUser actor, String publicId, ExerciseInput in) {
        Exercise e = findExercise(publicId);
        String before = e.getSolutionJson();
        e.apply(definition(in), Instant.now(clock));
        audit.record(actor.id(), "EXERCISE_UPDATED", "EXERCISE", e.getPublicId(), Map.of("solution", before),
                Map.of("solution", e.getSolutionJson()));
        if (e.getLessonId() != null) {
            lessons.findById(e.getLessonId()).ifPresent(l -> l.edit(l.getTitle(), l.getObjective(),
                    l.getExplanation(), l.getExamplesJson(), l.getVocabularyJson(), l.getEstimatedMinutes(),
                    Instant.now(clock)));
        }
        return toAdminExercise(e);
    }

    @PreAuthorize("hasAuthority('content:write')")
    @Transactional
    public AdminExercise changeExerciseStatus(CurrentUser actor, String publicId, ContentStatus status) {
        Exercise e = findExercise(publicId);
        String before = e.getStatus().name();
        e.changeStatus(status, Instant.now(clock));
        audit.record(actor.id(), "EXERCISE_STATUS_CHANGED", "EXERCISE", e.getPublicId(), Map.of("status", before),
                Map.of("status", status.name()));
        return toAdminExercise(e);
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private Exercise.Definition definition(ExerciseInput in) {
        JsonNode prompt = json.parseUserJson(in.promptJson(), "prompt", false);
        JsonNode solution = json.parseUserJson(in.solutionJson(), "solution", false);
        List<String> problems = ExerciseEvaluator.validateDefinition(in.type(), prompt, solution);
        if (!problems.isEmpty()) {
            throw ApiException.badRequest("INVALID_EXERCISE", "Definición inválida: " + String.join("; ", problems));
        }
        return new Exercise.Definition(in.type(), in.skill(), in.level(), in.instructions(), json.write(prompt),
                json.write(solution), in.feedback(), in.hint());
    }

    private String examples(String raw) {
        return json.write(json.parseUserJson(raw == null || raw.isBlank() ? "[]" : raw, "examples", true));
    }

    private String vocabulary(String raw) {
        return json.write(json.parseUserJson(raw == null || raw.isBlank() ? "[]" : raw, "vocabulary", true));
    }

    private static void validateSlug(String slug) {
        if (slug == null || slug.length() > 80 || !SLUG.matcher(slug).matches()) {
            throw ApiException.badRequest("INVALID_SLUG",
                    "El identificador solo admite minúsculas, números y guiones (máx. 80).");
        }
    }

    private Lesson findLesson(String publicId) {
        return lessons.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("LESSON_NOT_FOUND", "Lección no encontrada."));
    }

    private Exercise findExercise(String publicId) {
        return exercises.findByPublicId(publicId)
                .orElseThrow(() -> ApiException.notFound("EXERCISE_NOT_FOUND", "Ejercicio no encontrado."));
    }

    private AdminLesson toAdminLesson(Lesson l) {
        List<AdminExercise> ex = exercises.findByLessonIdOrderBySortOrderAsc(l.getId()).stream()
                .map(this::toAdminExercise).toList();
        return new AdminLesson(l.getPublicId(), l.getSlug(), l.getLevel(), l.getTitle(), l.getObjective(),
                l.getExplanation(), json.read(l.getExamplesJson()), json.read(l.getVocabularyJson()),
                l.getEstimatedMinutes(), l.getStatus().name(), l.getReviewedAt() != null, l.getContentVersion(), ex);
    }

    private AdminExercise toAdminExercise(Exercise e) {
        return new AdminExercise(e.getPublicId(), e.getSlug(), e.getType(), e.getSkill(), e.getLevel(),
                e.getInstructions(), json.read(e.getPromptJson()), json.read(e.getSolutionJson()), e.getFeedback(),
                e.getHint(), e.getStatus().name(), e.getSortOrder());
    }
}
