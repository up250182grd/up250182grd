package com.englishos.tutor.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo tutor (exportación y cierre de cuenta). */
@Component
class TutorPersonalData extends SqlPersonalData {

    TutorPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "profesor_texto";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("conversaciones", "select public_id, mode, topic, status, turns, started_at, closed_at from ai_conversations where user_id = ?");
        q.put("mensajes", "select c.public_id as conversacion, m.role, m.content, m.created_at from ai_messages m join ai_conversations c on c.id = m.conversation_id where c.user_id = ?");
        q.put("errores", "select public_id, original, correction, category, created_at, practice_count, resolved_at from learner_errors where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete from learner_errors where user_id = ?",
                "delete m from ai_messages m join ai_conversations c on c.id = m.conversation_id where c.user_id = ?",
                "update ai_conversations set topic = null where user_id = ?");
    }
}
