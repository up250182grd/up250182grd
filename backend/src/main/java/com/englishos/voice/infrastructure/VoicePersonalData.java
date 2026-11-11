package com.englishos.voice.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo voice (exportación y cierre de cuenta). */
@Component
class VoicePersonalData extends SqlPersonalData {

    VoicePersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "voz";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("llamadas", "select public_id, mode, topic, status, end_reason, created_at, connected_at, ended_at from voice_sessions where user_id = ?");
        q.put("transcripciones", "select s.public_id as llamada, t.role, t.content, t.created_at from voice_transcripts t join voice_sessions s on s.id = t.session_id where s.user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete t from voice_transcripts t join voice_sessions s on s.id = t.session_id where s.user_id = ?",
                "update voice_sessions set topic = null where user_id = ?");
    }
}
