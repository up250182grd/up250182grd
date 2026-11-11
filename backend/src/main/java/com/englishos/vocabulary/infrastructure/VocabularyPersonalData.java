package com.englishos.vocabulary.infrastructure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.englishos.shared.privacy.SqlPersonalData;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Datos personales del módulo vocabulary (exportación y cierre de cuenta). */
@Component
class VocabularyPersonalData extends SqlPersonalData {

    VocabularyPersonalData(JdbcTemplate jdbc) {
        super(jdbc);
    }

    @Override
    public String section() {
        return "vocabulario";
    }

    @Override
    protected Map<String, String> exportQueries() {
        Map<String, String> q = new LinkedHashMap<>();
        q.put("tarjetas", "select i.term, i.translation_es, c.learning_state, c.repetitions, c.interval_days, c.ease_factor, c.due_at, c.lapses, c.review_count from user_vocabulary_cards c join vocabulary_items i on i.id = c.item_id where c.user_id = ?");
        q.put("repasos", "select quality, duration_ms, reviewed_at from vocabulary_reviews where user_id = ?");
        return q;
    }

    @Override
    protected List<String> eraseStatements() {
        return List.of(
                "delete from vocabulary_reviews where user_id = ?",
                "delete from user_vocabulary_cards where user_id = ?");
    }
}
