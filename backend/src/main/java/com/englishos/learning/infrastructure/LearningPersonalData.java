package com.englishos.learning.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo learning (exportación y cierre de cuenta). */
@Component
class LearningPersonalData extends SqlPersonalData {

    LearningPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "aprendizaje";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("planes", "select source, start_level, is_active, created_at from learning_plans where user_id = ?");
        q.put("progreso_lecciones", "select l.slug, p.state, p.best_score, p.last_score, p.completed_runs, p.mastered_at, p.last_activity_at from lesson_progress p join lessons l on l.id = p.lesson_id where p.user_id = ?");
        q.put("ejecuciones", "select r.public_id, l.slug, r.score, r.local_date, r.started_at, r.completed_at from lesson_runs r join lessons l on l.id = r.lesson_id where r.user_id = ?");
        q.put("intentos", "select e.public_id as ejercicio, a.answer, a.is_correct, a.score, a.hints_used, a.duration_ms, a.created_at from exercise_attempts a join exercises e on e.id = a.exercise_id where a.user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete from exercise_attempts where user_id = ?",
                "delete from lesson_runs where user_id = ?",
                "delete from lesson_progress where user_id = ?",
                "delete from learning_plans where user_id = ?");
    }
}
