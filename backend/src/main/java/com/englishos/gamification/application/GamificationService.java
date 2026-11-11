package com.englishos.gamification.application;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.englishos.gamification.domain.Badges;
import com.englishos.gamification.domain.Streak;
import com.englishos.gamification.domain.XpRules;
import com.englishos.gamification.infrastructure.GameStore;
import com.englishos.identity.api.LearnerProfiles;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * XP, rachas e insignias (Fase 8). Cada hecho se registra una sola vez (por su id de origen): las entregas repetidas
 * de un evento no suman de nuevo. El XP nunca cambia el nivel MCER (RN-44).
 */
@Service
public class GamificationService {

    private final GameStore store;
    private final LearnerProfiles profiles;
    private final Clock clock;

    GamificationService(GameStore store, LearnerProfiles profiles, Clock clock) {
        this.store = store;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Un hecho con sus efectos; solo se aplican si el hecho es nuevo. */
    public record Fact(Long userId, String type, String sourceId, String ref, Instant at, long activityMs, int lessons,
            int reviews, int lessonsCompleted, int lessonsMastered, int wordsLearned, int errorsResolved,
            int conversations, boolean diagnostic) {

        static Fact of(Long userId, String type, String sourceId, Instant at) {
            return new Fact(userId, type, sourceId, null, at, 0, 0, 0, 0, 0, 0, 0, 0, false);
        }

        Fact ref(String r) {
            return new Fact(userId, type, sourceId, r, at, activityMs, lessons, reviews, lessonsCompleted,
                    lessonsMastered, wordsLearned, errorsResolved, conversations, diagnostic);
        }

        Fact activity(long ms, int l, int r) {
            return new Fact(userId, type, sourceId, ref, at, ms, l, r, lessonsCompleted, lessonsMastered,
                    wordsLearned, errorsResolved, conversations, diagnostic);
        }

        Fact counters(int completed, int mastered, int words, int errors, int convs, boolean diag) {
            return new Fact(userId, type, sourceId, ref, at, activityMs, lessons, reviews, completed, mastered,
                    words, errors, convs, diag);
        }
    }

    /**
     * Aplica un hecho. {@code xpFor} recibe la fecha local y calcula el XP (puede depender de lo ya hecho hoy).
     * Devuelve las insignias nuevas.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public List<String> apply(Fact f, java.util.function.ToIntFunction<LocalDate> xpFor) {
        ZoneId zone = profiles.zoneOf(f.userId());
        LocalDate date = LocalDate.ofInstant(f.at(), zone);
        int xp = Math.max(0, xpFor.applyAsInt(date));
        if (!store.recordEvent(f.userId(), f.type(), f.sourceId(), f.ref(), date, xp, f.at())) {
            return List.of(); // entrega repetida: ya aplicado
        }
        if (f.activityMs() > 0 || f.lessons() > 0 || f.reviews() > 0) {
            store.addActivity(f.userId(), date, f.activityMs(), f.lessons(), f.reviews());
        }
        store.bump(f.userId(), xp, f.lessonsCompleted(), f.lessonsMastered(), f.wordsLearned(), f.errorsResolved(),
                f.conversations(), f.diagnostic(), Instant.now(clock));
        store.raiseBestStreak(f.userId(), streak(f.userId(), zone).best());
        Badges.Stats stats = store.stats(f.userId());
        List<String> awarded = new ArrayList<>();
        for (Badges.Badge b : Badges.ALL) {
            if (b.earned(stats) && store.award(f.userId(), b.code(), Instant.now(clock))) {
                awarded.add(b.code());
            }
        }
        return awarded;
    }

    /** XP de una ejecución de lección: decreciente si se repite la misma lección el mismo día. */
    public int lessonRunXp(Long userId, Long lessonId, LocalDate date) {
        return XpRules.lessonRun(store.countEvents(userId, "LESSON_RUN", String.valueOf(lessonId), date) + 1);
    }

    Streak.Result streak(Long userId, ZoneId zone) {
        Set<LocalDate> qualified = new HashSet<>();
        for (GameStore.DayActivity d : store.activity(userId).values()) {
            int minutes = (int) (d.activityMs() / 60_000);
            if (Streak.qualifies(minutes, d.lessons(), d.reviews())) {
                qualified.add(d.date());
            }
        }
        return Streak.compute(qualified, LocalDate.now(clock.withZone(zone)));
    }

    // ---- Vista ---------------------------------------------------------------------------------

    public record DayView(LocalDate date, String status) {
    }

    public record TodayProgress(int minutes, int lessons, int reviews, int minMinutes, int minLessons,
            int minReviews, boolean qualified) {
    }

    public record StreakView(int current, int best, boolean restUsedThisWeek, TodayProgress today,
            List<DayView> last28Days) {
    }

    public record BadgeView(String code, String title, String description, int goal, int progress, boolean earned,
            Instant awardedAt) {
    }

    public record XpItem(String source, int xp, Instant at) {
    }

    public record GamificationView(long xpTotal, int xpToday, StreakView streak, List<BadgeView> badges,
            int badgesEarned, List<XpItem> recentXp, int xpRulesVersion) {
    }

    @Transactional(readOnly = true)
    public GamificationView view(Long userId) {
        ZoneId zone = profiles.zoneOf(userId);
        LocalDate today = LocalDate.now(clock.withZone(zone));
        Streak.Result s = streak(userId, zone);
        GameStore.DayActivity act = store.activity(userId).getOrDefault(today,
                new GameStore.DayActivity(today, 0, 0, 0));
        int minutes = (int) (act.activityMs() / 60_000);
        TodayProgress tp = new TodayProgress(minutes, act.lessons(), act.reviews(), Streak.MIN_MINUTES,
                Streak.MIN_LESSONS, Streak.MIN_REVIEWS, s.todayQualified());
        List<DayView> days = new ArrayList<>();
        for (LocalDate d = today.minusDays(27); !d.isAfter(today); d = d.plusDays(1)) {
            Streak.DayStatus st = s.days().getOrDefault(d, d.equals(today) ? Streak.DayStatus.PENDING
                    : Streak.DayStatus.NONE);
            days.add(new DayView(d, st.name()));
        }
        Badges.Stats stats = store.stats(userId);
        // La mejor racha guardada puede quedar por debajo si el cálculo vivo es mayor (p. ej., día de hoy).
        Badges.Stats live = new Badges.Stats(stats.lessonsCompleted(), stats.lessonsMastered(), stats.wordsLearned(),
                stats.errorsResolved(), stats.conversations(), stats.diagnosticDone(),
                Math.max(stats.bestStreak(), s.best()));
        Map<String, Instant> earned = store.badges(userId);
        List<BadgeView> badges = Badges.ALL.stream()
                .map(b -> new BadgeView(b.code(), b.title(), b.description(), b.goal(), b.progressOf(live),
                        earned.containsKey(b.code()), earned.get(b.code())))
                .toList();
        List<XpItem> recent = store.recentXp(userId, 10).stream()
                .map(e -> new XpItem(e.sourceType(), e.xp(), e.at())).toList();
        return new GamificationView(store.xpTotal(userId), store.xpOn(userId, today),
                new StreakView(s.current(), Math.max(s.best(), stats.bestStreak()), s.restUsedThisWeek(), tp, days),
                badges, earned.size(), recent, XpRules.VERSION);
    }
}
