package com.englishos.gamification.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo gamification (exportación y cierre de cuenta). */
@Component
class GamificationPersonalData extends SqlPersonalData {

    GamificationPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "logros";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("estadisticas", "select xp_total, lessons_completed, lessons_mastered, words_learned, errors_resolved, conversations, diagnostic_done, best_streak from user_game_stats where user_id = ?");
        q.put("insignias", "select code, awarded_at from user_badges where user_id = ?");
        q.put("actividad_diaria", "select local_date, activity_ms, lessons, reviews from daily_activity where user_id = ?");
        q.put("eventos_xp", "select source_type, xp, local_date, created_at from game_events where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete from game_events where user_id = ?",
                "delete from daily_activity where user_id = ?",
                "delete from user_badges where user_id = ?",
                "delete from user_game_stats where user_id = ?");
    }
}
