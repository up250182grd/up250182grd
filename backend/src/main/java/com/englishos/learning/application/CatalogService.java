package com.englishos.learning.application;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import tools.jackson.databind.JsonNode;

import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.Localized;
import com.englishos.learning.api.Skill;
import com.englishos.learning.domain.ContentStatus;
import com.englishos.learning.domain.Course;
import com.englishos.learning.domain.CourseModule;
import com.englishos.learning.domain.Exercise;
import com.englishos.learning.domain.ExerciseType;
import com.englishos.learning.domain.LearningPlan;
import com.englishos.learning.domain.Lesson;
import com.englishos.learning.domain.LessonProgress;
import com.englishos.learning.infrastructure.CourseModuleRepository;
import com.englishos.learning.infrastructure.CourseRepository;
import com.englishos.learning.infrastructure.ExerciseRepository;
import com.englishos.learning.infrastructure.LearningPlanRepository;
import com.englishos.learning.infrastructure.LessonProgressRepository;
import com.englishos.learning.infrastructure.LessonRepository;
import com.englishos.shared.error.ApiException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lectura del contenido publicado para el alumno, con su progreso (RF-CRS-1, RF-CRS-5). Las soluciones de los
 * ejercicios nunca forman parte de estas vistas (RF-EXR-3).
 */
@Service
public class CatalogService {

    public static final String NOT_STARTED = "NOT_STARTED";

    private final CourseRepository courses;
    private final CourseModuleRepository modules;
    private final LessonRepository lessons;
    private final ExerciseRepository exercises;
    private final LessonProgressRepository progress;
    private final LearningPlanRepository plans;
    private final Json json;

    CatalogService(CourseRepository courses, CourseModuleRepository modules, LessonRepository lessons,
            ExerciseRepository exercises, LessonProgressRepository progress, LearningPlanRepository plans, Json json) {
        this.courses = courses;
        this.modules = modules;
        this.lessons = lessons;
        this.exercises = exercises;
        this.progress = progress;
        this.plans = plans;
        this.json = json;
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record LessonSummary(String publicId, CefrLevel level, Localized title, int estimatedMinutes,
            String state, BigDecimal bestScore) {
    }

    public record ModuleView(String publicId, Localized title, Localized objective, List<LessonSummary> lessons) {
    }

    public record CourseView(String publicId, CefrLevel level, Localized title, Localized description,
            boolean inPlan, List<ModuleView> modules) {
    }

    public record ExerciseView(String publicId, ExerciseType type, Skill skill, Localized instructions,
            JsonNode prompt, Localized hint) {
    }

    public record PrerequisiteView(String publicId, Localized title, String state) {
    }

    public record ProgressView(String state, BigDecimal bestScore, BigDecimal lastScore, int completedRuns) {
    }

    public record LessonView(String publicId, CefrLevel level, Localized courseTitle, Localized moduleTitle,
            Localized title, Localized objective, Localized explanation, JsonNode examples, JsonNode vocabulary,
            int estimatedMinutes, List<PrerequisiteView> prerequisites, ProgressView progress,
            List<ExerciseView> exercises) {
    }

    public record RecommendedLesson(String publicId, Localized title, Localized courseTitle, CefrLevel level,
            int estimatedMinutes, String state) {
    }

    public record LearningSummary(CefrLevel planStartLevel, String planSource, RecommendedLesson recommended,
            int totalLessons, int mastered, int practiced, int inProgress, int notStarted) {
    }

    // ---- Consultas ----------------------------------------------------------------------------

    @Transactional(readOnly = true)
    public List<CourseView> courseTree(Long userId) {
        Tree tree = publishedTree();
        Map<Long, LessonProgress> prog = progressByLesson(userId);
        List<Long> planCourseIds = planCourseIds(userId);
        return tree.courses.stream().map(c -> new CourseView(c.getPublicId(), c.getLevel(), c.getTitle(),
                c.getDescription(), planCourseIds.contains(c.getId()),
                tree.modulesOf(c).stream().map(m -> new ModuleView(m.getPublicId(), m.getTitle(), m.getObjective(),
                        tree.lessonsOf(m).stream().map(l -> summary(l, prog.get(l.getId()))).toList())).toList()))
                .toList();
    }

    @Transactional(readOnly = true)
    public LessonView lessonDetail(Long userId, String lessonPublicId) {
        Lesson lesson = lessons.findByPublicId(lessonPublicId)
                .filter(l -> l.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(CatalogService::lessonNotFound);
        CourseModule module = modules.findById(lesson.getModuleId())
                .filter(m -> m.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(CatalogService::lessonNotFound);
        Course course = courses.findById(module.getCourseId())
                .filter(c -> c.getStatus() == ContentStatus.PUBLISHED)
                .orElseThrow(CatalogService::lessonNotFound);

        Map<Long, LessonProgress> prog = progressByLesson(userId);
        List<PrerequisiteView> prereqs = lessons.findByIdIn(lesson.getPrerequisiteIds()).stream()
                .filter(p -> p.getStatus() == ContentStatus.PUBLISHED)
                .map(p -> new PrerequisiteView(p.getPublicId(), p.getTitle(), stateOf(prog.get(p.getId()))))
                .toList();
        LessonProgress mine = prog.get(lesson.getId());
        ProgressView progressView = mine == null
                ? new ProgressView(NOT_STARTED, null, null, 0)
                : new ProgressView(mine.getState().name(), mine.getBestScore(), mine.getLastScore(),
                        mine.getCompletedRuns());

        List<ExerciseView> exerciseViews = exercises
                .findByLessonIdAndStatusOrderBySortOrderAsc(lesson.getId(), ContentStatus.PUBLISHED).stream()
                .map(this::exerciseView)
                .toList();

        return new LessonView(lesson.getPublicId(), lesson.getLevel(), course.getTitle(), module.getTitle(),
                lesson.getTitle(), lesson.getObjective(), lesson.getExplanation(), json.read(lesson.getExamplesJson()),
                json.read(lesson.getVocabularyJson()), lesson.getEstimatedMinutes(), prereqs, progressView,
                exerciseViews);
    }

    @Transactional(readOnly = true)
    public LearningSummary summary(Long userId) {
        Tree tree = publishedTree();
        Map<Long, LessonProgress> prog = progressByLesson(userId);
        var plan = plans.findCurrent(userId);
        List<Long> planIds = plan.map(p -> parseIds(p.getCourseIdsJson())).orElse(List.of());

        List<Course> scope = planIds.isEmpty() ? tree.courses
                : planIds.stream().map(id -> tree.courseById.get(id)).filter(c -> c != null).toList();

        RecommendedLesson recommended = null;
        int total = 0;
        int mastered = 0;
        int practiced = 0;
        int inProgress = 0;
        for (Course c : scope) {
            for (CourseModule m : tree.modulesOf(c)) {
                for (Lesson l : tree.lessonsOf(m)) {
                    total++;
                    String state = stateOf(prog.get(l.getId()));
                    switch (state) {
                        case "MASTERED" -> mastered++;
                        case "PRACTICED" -> practiced++;
                        case "IN_PROGRESS" -> inProgress++;
                        default -> {
                        }
                    }
                    if (recommended == null && !"MASTERED".equals(state)) {
                        recommended = new RecommendedLesson(l.getPublicId(), l.getTitle(), c.getTitle(), l.getLevel(),
                                l.getEstimatedMinutes(), state);
                    }
                }
            }
        }
        return new LearningSummary(plan.map(LearningPlan::getStartLevel).orElse(null),
                plan.map(LearningPlan::getSource).orElse(null), recommended, total, mastered, practiced, inProgress,
                total - mastered - practiced - inProgress);
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    ExerciseView exerciseView(Exercise e) {
        return new ExerciseView(e.getPublicId(), e.getType(), e.getSkill(), e.getInstructions(),
                json.read(e.getPromptJson()), e.getHint());
    }

    private List<Long> planCourseIds(Long userId) {
        return plans.findCurrent(userId).map(p -> parseIds(p.getCourseIdsJson())).orElse(List.of());
    }

    private List<Long> parseIds(String jsonArray) {
        List<Long> ids = new ArrayList<>();
        json.read(jsonArray).forEach(n -> ids.add(n.asLong()));
        return ids;
    }

    private Map<Long, LessonProgress> progressByLesson(Long userId) {
        return progress.findByUserId(userId).stream()
                .collect(Collectors.toMap(LessonProgress::getLessonId, Function.identity()));
    }

    private static LessonSummary summary(Lesson l, LessonProgress p) {
        return new LessonSummary(l.getPublicId(), l.getLevel(), l.getTitle(), l.getEstimatedMinutes(), stateOf(p),
                p == null ? null : p.getBestScore());
    }

    static String stateOf(LessonProgress p) {
        return p == null ? NOT_STARTED : p.getState().name();
    }

    private static ApiException lessonNotFound() {
        return ApiException.notFound("LESSON_NOT_FOUND", "Lección no encontrada.");
    }

    /** Contenido publicado completo (cursos → módulos → lecciones), en orden. Volumen pequeño: se carga entero. */
    private Tree publishedTree() {
        List<Course> cs = courses.findByStatusOrderByLevelAscSortOrderAsc(ContentStatus.PUBLISHED);
        List<CourseModule> ms = cs.isEmpty() ? List.of()
                : modules.findByCourseIdInOrderBySortOrderAsc(cs.stream().map(Course::getId).toList()).stream()
                        .filter(m -> m.getStatus() == ContentStatus.PUBLISHED).toList();
        List<Lesson> ls = ms.isEmpty() ? List.of()
                : lessons.findByModuleIdInOrderBySortOrderAsc(ms.stream().map(CourseModule::getId).toList()).stream()
                        .filter(l -> l.getStatus() == ContentStatus.PUBLISHED).toList();
        return new Tree(cs, ms, ls);
    }

    private static final class Tree {

        final List<Course> courses;
        final Map<Long, Course> courseById;
        final Map<Long, List<CourseModule>> modulesByCourse;
        final Map<Long, List<Lesson>> lessonsByModule;

        Tree(List<Course> courses, List<CourseModule> modules, List<Lesson> lessons) {
            this.courses = courses;
            this.courseById = courses.stream().collect(Collectors.toMap(Course::getId, Function.identity()));
            this.modulesByCourse = modules.stream().sorted(Comparator.comparingInt(CourseModule::getSortOrder))
                    .collect(Collectors.groupingBy(CourseModule::getCourseId));
            this.lessonsByModule = lessons.stream().sorted(Comparator.comparingInt(Lesson::getSortOrder))
                    .collect(Collectors.groupingBy(Lesson::getModuleId));
        }

        List<CourseModule> modulesOf(Course c) {
            return modulesByCourse.getOrDefault(c.getId(), List.of());
        }

        List<Lesson> lessonsOf(CourseModule m) {
            return lessonsByModule.getOrDefault(m.getId(), List.of());
        }
    }
}
