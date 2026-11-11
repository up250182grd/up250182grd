package com.englishos.gamification.infrastructure;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.englishos.gamification.domain.Badges;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** Acceso a las tablas de gamificación con operaciones atómicas e idempotentes (INSERT IGNORE / UPSERT). */
@Repository
public class GameStore {

    private final JdbcTemplate jdbc;

    GameStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record DayActivity(LocalDate date, long activityMs, int lessons, int reviews) {
    }

    public record EventRow(String sourceType, String ref, int xp, Instant at) {
    }

    /** Registra el evento si es nuevo. Devuelve false si ya se había procesado (entrega repetida). */
    public boolean recordEvent(Long userId, String type, String sourceId, String ref, LocalDate date, int xp,
            Instant now) {
        return jdbc.update("""
                insert ignore into game_events (user_id, source_type, source_id, ref, local_date, xp, created_at)
                values (?, ?, ?, ?, ?, ?, ?)""", userId, type, sourceId, ref, Date.valueOf(date), xp,
                Timestamp.from(now)) == 1;
    }

    public int countEvents(Long userId, String type, String ref, LocalDate date) {
        Integer n = jdbc.queryForObject("""
                select count(*) from game_events where user_id = ? and source_type = ? and ref = ? and local_date = ?""",
                Integer.class, userId, type, ref, Date.valueOf(date));
        return n == null ? 0 : n;
    }

    public void addActivity(Long userId, LocalDate date, long ms, int lessons, int reviews) {
        jdbc.update("""
                insert into daily_activity (user_id, local_date, activity_ms, lessons, reviews)
                values (?, ?, ?, ?, ?) as n
                on duplicate key update activity_ms = daily_activity.activity_ms + n.activity_ms,
                    lessons = daily_activity.lessons + n.lessons, reviews = daily_activity.reviews + n.reviews""",
                userId, Date.valueOf(date), ms, lessons, reviews);
    }

    public void bump(Long userId, int xp, int lessonsCompleted, int lessonsMastered, int wordsLearned,
            int errorsResolved, int conversations, boolean diagnostic, Instant now) {
        jdbc.update("""
                insert into user_game_stats (user_id, xp_total, lessons_completed, lessons_mastered, words_learned,
                    errors_resolved, conversations, diagnostic_done, best_streak, updated_at)
                values (?, ?, ?, ?, ?, ?, ?, ?, 0, ?) as n
                on duplicate key update xp_total = user_game_stats.xp_total + n.xp_total,
                    lessons_completed = user_game_stats.lessons_completed + n.lessons_completed,
                    lessons_mastered = user_game_stats.lessons_mastered + n.lessons_mastered,
                    words_learned = user_game_stats.words_learned + n.words_learned,
                    errors_resolved = user_game_stats.errors_resolved + n.errors_resolved,
                    conversations = user_game_stats.conversations + n.conversations,
                    diagnostic_done = user_game_stats.diagnostic_done or n.diagnostic_done,
                    updated_at = n.updated_at""",
                userId, xp, lessonsCompleted, lessonsMastered, wordsLearned, errorsResolved, conversations, diagnostic,
                Timestamp.from(now));
    }

    public void raiseBestStreak(Long userId, int best) {
        jdbc.update("update user_game_stats set best_streak = greatest(best_streak, ?) where user_id = ?", best, userId);
    }

    public Map<LocalDate, DayActivity> activity(Long userId) {
        Map<LocalDate, DayActivity> out = new LinkedHashMap<>();
        jdbc.query("""
                select local_date, activity_ms, lessons, reviews from daily_activity where user_id = ?
                order by local_date""", rs -> {
                    LocalDate d = rs.getDate(1).toLocalDate();
                    out.put(d, new DayActivity(d, rs.getLong(2), rs.getInt(3), rs.getInt(4)));
                }, userId);
        return out;
    }

    public long xpTotal(Long userId) {
        List<Long> v = jdbc.queryForList("select xp_total from user_game_stats where user_id = ?", Long.class, userId);
        return v.isEmpty() ? 0 : v.getFirst();
    }

    public int xpOn(Long userId, LocalDate date) {
        Integer n = jdbc.queryForObject("select coalesce(sum(xp), 0) from game_events where user_id = ? and local_date = ?",
                Integer.class, userId, Date.valueOf(date));
        return n == null ? 0 : n;
    }

    public Badges.Stats stats(Long userId) {
        List<Badges.Stats> rows = jdbc.query("""
                select lessons_completed, lessons_mastered, words_learned, errors_resolved, conversations,
                       diagnostic_done, best_streak from user_game_stats where user_id = ?""",
                (rs, i) -> new Badges.Stats(rs.getInt(1), rs.getInt(2), rs.getInt(3), rs.getInt(4), rs.getInt(5),
                        rs.getBoolean(6), rs.getInt(7)), userId);
        return rows.isEmpty() ? new Badges.Stats(0, 0, 0, 0, 0, false, 0) : rows.getFirst();
    }

    public Map<String, Instant> badges(Long userId) {
        Map<String, Instant> out = new LinkedHashMap<>();
        jdbc.query("select code, awarded_at from user_badges where user_id = ? order by awarded_at",
                rs -> {
                    out.put(rs.getString(1), rs.getTimestamp(2).toInstant());
                }, userId);
        return out;
    }

    public boolean award(Long userId, String code, Instant now) {
        return jdbc.update("insert ignore into user_badges (user_id, code, awarded_at) values (?, ?, ?)", userId, code,
                Timestamp.from(now)) == 1;
    }

    public List<EventRow> recentXp(Long userId, int limit) {
        return jdbc.query("""
                select source_type, ref, xp, created_at from game_events where user_id = ? and xp > 0
                order by id desc limit ?""",
                (rs, i) -> new EventRow(rs.getString(1), rs.getString(2), rs.getInt(3),
                        rs.getTimestamp(4).toInstant()), userId, limit);
    }

    public Set<LocalDate> datesWithEvents(Long userId) {
        return new HashSet<>(jdbc.queryForList("select distinct local_date from game_events where user_id = ?",
                LocalDate.class, userId));
    }
}
