package com.englishos.recommendation.application;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.assessment.api.LearnerLevels;
import com.englishos.audit.AuditService;
import com.englishos.identity.api.CurrentUser;
import com.englishos.identity.api.LearnerProfiles;
import com.englishos.learning.api.CefrLevel;
import com.englishos.learning.api.LearningProgressApi;
import com.englishos.learning.api.Skill;
import com.englishos.recommendation.application.ActivityEstimator.Event;
import com.englishos.recommendation.application.ActivityEstimator.Kind;
import com.englishos.recommendation.domain.RecommendationEngine;
import com.englishos.recommendation.domain.RecommendationEngine.Recommendation;
import com.englishos.recommendation.domain.RecommendationEngine.Type;
import com.englishos.recommendation.domain.WeightsVersion;
import com.englishos.recommendation.infrastructure.WeightsVersionRepository;
import com.englishos.shared.error.ApiException;
import com.englishos.tutor.api.LearnerErrorsApi;
import com.englishos.vocabulary.api.VocabularyApi;
import com.englishos.wallet.api.WalletApi;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Reúne el estado del alumno desde los demás módulos (solo por sus API públicas), aplica el motor de reglas y arma la
 * vista de progreso del tablero. Todo con datos reales; los minutos de estudio son una estimación y se presentan así.
 */
@Service
public class RecommendationService {

    static final int ACTIVITY_DAYS = 14;

    private final WeightsVersionRepository weights;
    private final LearningProgressApi learning;
    private final LearnerLevels levels;
    private final VocabularyApi vocabulary;
    private final LearnerErrorsApi errors;
    private final LearnerProfiles profiles;
    private final WalletApi wallet;
    private final AuditService audit;
    private final JsonMapper json;
    private final Clock clock;
    private final BigDecimal tutorMinimumBalance;

    RecommendationService(WeightsVersionRepository weights, LearningProgressApi learning, LearnerLevels levels,
            VocabularyApi vocabulary, LearnerErrorsApi errors, LearnerProfiles profiles, WalletApi wallet,
            AuditService audit, JsonMapper json, Clock clock,
            @Value("${app.tutor.initial-reserve-mxn:5.00}") BigDecimal tutorMinimumBalance) {
        this.weights = weights;
        this.learning = learning;
        this.levels = levels;
        this.vocabulary = vocabulary;
        this.errors = errors;
        this.profiles = profiles;
        this.wallet = wallet;
        this.audit = audit;
        this.json = json;
        this.clock = clock;
        this.tutorMinimumBalance = tutorMinimumBalance;
    }

    // ---- Vistas -------------------------------------------------------------------------------

    public record RecommendationView(String type, int score, String title, String reason, String actionPath,
            int estimatedMinutes) {
    }

    public record RecommendationsView(List<RecommendationView> items, int minutesToday, int dailyGoalMinutes,
            int weightsVersion, int engineVersion) {
    }

    public record ActivityDay(LocalDate date, int exercises, int reviews, int tutorMessages, int minutes) {
    }

    public record SkillLevelItem(String skill, String level) {
    }

    public record LearnerProgressView(List<ActivityDay> activity, int minutesToday, int dailyGoalMinutes,
            int activeDaysLast14, VocabularyApi.VocabularyStats vocabulary, Map<String, Integer> openErrorsByCategory,
            List<SkillLevelItem> skills) {
    }

    public record RecommendationWeightsView(Long id, Map<String, Integer> weights, String note, Long createdBy, Instant createdAt) {
    }

    // ---- Alumno -------------------------------------------------------------------------------

    @Transactional
    public RecommendationsView recommendations(CurrentUser user) {
        Long userId = user.id();
        ZoneId zone = profiles.zoneOf(userId);
        Instant now = Instant.now(clock);
        int goal = profiles.dailyGoalMinutes(userId);
        int today = minutesSince(userId, LocalDate.ofInstant(now, zone).atStartOfDay(zone).toInstant());

        vocabulary.sync(userId);
        VocabularyApi.VocabularyStats vocab = vocabulary.stats(userId);
        Map<String, Integer> open = errors.openErrorsByCategory(userId);
        Map.Entry<String, Integer> top = open.entrySet().stream()
                // Mayor cantidad; en empate, la categoría alfabéticamente primera (determinista).
                .max(Comparator.comparing((Map.Entry<String, Integer> e) -> e.getValue())
                        .thenComparing(Map.Entry::getKey, Comparator.reverseOrder()))
                .orElse(null);
        Map<Skill, CefrLevel> skillLevels = levels.levelsBySkill(userId);
        String weakest = null;
        int gap = 0;
        if (skillLevels.size() > 1) {
            var min = skillLevels.entrySet().stream().min(Map.Entry.comparingByValue()).orElseThrow();
            var max = skillLevels.values().stream().max(Comparator.naturalOrder()).orElseThrow();
            gap = max.ordinal() - min.getValue().ordinal();
            weakest = gap > 0 ? min.getKey().name() : null;
        }
        var next = learning.nextLesson(userId)
                .map(l -> new RecommendationEngine.NextLesson(l.publicId(), l.title().es(), l.estimatedMinutes()))
                .orElse(null);
        boolean canUseTutor = profiles.isEmailVerified(userId)
                && (user.isAdmin() || wallet.available(userId).compareTo(tutorMinimumBalance) >= 0);

        var snapshot = new RecommendationEngine.Snapshot(!skillLevels.isEmpty(), vocab.dueNow(),
                vocab.newAvailableToday(), open.values().stream().mapToInt(Integer::intValue).sum(),
                top == null ? null : top.getKey(), top == null ? 0 : top.getValue(), next, weakest, gap,
                Math.max(0, goal - today), canUseTutor);
        WeightsVersion current = currentVersion();
        List<RecommendationView> items = RecommendationEngine.recommend(snapshot, toEngine(parse(current))).stream()
                .map(RecommendationService::view).toList();
        return new RecommendationsView(items, today, goal, current.getId().intValue(), RecommendationEngine.VERSION);
    }

    @Transactional(readOnly = true)
    public LearnerProgressView progress(CurrentUser user) {
        Long userId = user.id();
        ZoneId zone = profiles.zoneOf(userId);
        LocalDate todayDate = LocalDate.now(clock.withZone(zone));
        LocalDate from = todayDate.minusDays(ACTIVITY_DAYS - 1L);
        List<ActivityEstimator.Day> days = ActivityEstimator.byDay(
                events(userId, from.atStartOfDay(zone).toInstant()), from, todayDate, zone);
        List<ActivityDay> activity = days.stream()
                .map(d -> new ActivityDay(d.date(), d.exercises(), d.reviews(), d.tutorMessages(), d.minutes()))
                .toList();
        int active = (int) activity.stream().filter(d -> d.exercises() + d.reviews() + d.tutorMessages() > 0).count();
        List<SkillLevelItem> skills = new ArrayList<>();
        levels.levelsBySkill(userId).forEach((k, v) -> skills.add(new SkillLevelItem(k.name(), v.name())));
        return new LearnerProgressView(activity, activity.getLast().minutes(), profiles.dailyGoalMinutes(userId), active,
                vocabulary.stats(userId), errors.openErrorsByCategory(userId), skills);
    }

    // ---- Administración -----------------------------------------------------------------------

    @PreAuthorize("hasAuthority('recommendation:write')")
    @Transactional(readOnly = true)
    public List<RecommendationWeightsView> weightsHistory() {
        return weights.findTop20ByOrderByIdDesc().stream()
                .map(v -> new RecommendationWeightsView(v.getId(), parse(v), v.getNote(), v.getCreatedBy(), v.getCreatedAt()))
                .toList();
    }

    @PreAuthorize("hasAuthority('recommendation:write')")
    @Transactional
    public RecommendationWeightsView updateWeights(CurrentUser actor, Map<String, Integer> proposed, String note) {
        if (note == null || note.strip().length() < 5 || note.length() > 500) {
            throw ApiException.badRequest("NOTE_REQUIRED", "Explica el motivo del cambio (5 a 500 caracteres).");
        }
        Map<String, Integer> clean = new LinkedHashMap<>();
        for (Type t : Type.values()) {
            String key = RecommendationEngine.WEIGHT_KEYS.get(t);
            Integer v = proposed == null ? null : proposed.get(key);
            if (v == null || v < 0 || v > 100) {
                throw ApiException.badRequest("INVALID_WEIGHTS", "Cada peso debe ser un entero de 0 a 100 (" + key + ").");
            }
            clean.put(key, v);
        }
        if (proposed.size() != clean.size()) {
            throw ApiException.badRequest("INVALID_WEIGHTS", "Hay pesos desconocidos.");
        }
        WeightsVersion before = currentVersion();
        WeightsVersion saved = weights.save(new WeightsVersion(json.writeValueAsString(clean), note.strip(),
                actor.id(), Instant.now(clock)));
        audit.record(actor.id(), "RECOMMENDATION_WEIGHTS_UPDATED", "RECOMMENDATION_WEIGHTS",
                String.valueOf(saved.getId()), parse(before), Map.of("weights", clean, "note", saved.getNote()));
        return new RecommendationWeightsView(saved.getId(), clean, saved.getNote(), saved.getCreatedBy(), saved.getCreatedAt());
    }

    // ---- Apoyo --------------------------------------------------------------------------------

    private WeightsVersion currentVersion() {
        return weights.findFirstByOrderByIdDesc()
                .orElseThrow(() -> new IllegalStateException("No hay pesos del recomendador (migración V7)."));
    }

    private Map<String, Integer> parse(WeightsVersion v) {
        Map<String, Integer> out = new LinkedHashMap<>();
        JsonNode node = json.readTree(v.getWeights());
        node.properties().forEach(e -> out.put(e.getKey(), e.getValue().asInt()));
        return out;
    }

    private static Map<Type, Integer> toEngine(Map<String, Integer> byKey) {
        Map<Type, Integer> out = new EnumMap<>(Type.class);
        RecommendationEngine.WEIGHT_KEYS.forEach((t, k) -> out.put(t, byKey.getOrDefault(k, 0)));
        return out;
    }

    private static RecommendationView view(Recommendation r) {
        return new RecommendationView(r.type().name(), r.score(), r.title(), r.reason(), r.actionPath(),
                r.estimatedMinutes());
    }

    private List<Event> events(Long userId, Instant from) {
        List<Event> out = new ArrayList<>();
        learning.attemptsSince(userId, from).forEach(a -> out.add(
                new Event(a.at(), a.durationMs(), ActivityEstimator.EXERCISE_DEFAULT_MS, Kind.EXERCISE)));
        vocabulary.reviewsSince(userId, from).forEach(r -> out.add(
                new Event(r.at(), r.durationMs(), ActivityEstimator.REVIEW_DEFAULT_MS, Kind.REVIEW)));
        errors.tutorMessageTimesSince(userId, from).forEach(t -> out.add(
                new Event(t, null, ActivityEstimator.TUTOR_MESSAGE_MS, Kind.TUTOR_MESSAGE)));
        return out;
    }

    private int minutesSince(Long userId, Instant from) {
        int ms = events(userId, from).stream().mapToInt(ActivityEstimator::millis).sum();
        return (ms + 30_000) / 60_000;
    }
}
