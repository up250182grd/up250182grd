package com.englishos.assessment.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo assessment (exportación y cierre de cuenta). */
@Component
class AssessmentPersonalData extends SqlPersonalData {

    AssessmentPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "diagnostico";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("sesiones", "select public_id, status, started_at, completed_at, result from assessment_sessions where user_id = ?");
        q.put("respuestas", "select s.public_id as sesion, r.skill, r.item_level, r.answer, r.is_correct, r.created_at from assessment_responses r join assessment_sessions s on s.id = r.session_id where s.user_id = ?");
        q.put("niveles", "select skill, estimated_level, confidence, evidence_count, source, updated_at from user_skill_levels where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete r from assessment_responses r join assessment_sessions s on s.id = r.session_id where s.user_id = ?",
                "delete from assessment_sessions where user_id = ?",
                "delete from user_skill_levels where user_id = ?");
    }
}
