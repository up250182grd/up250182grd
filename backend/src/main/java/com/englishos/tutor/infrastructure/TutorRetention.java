package com.englishos.tutor.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;

import com.englishos.shared.privacy.RetentionPolicy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Borra los mensajes de conversaciones CERRADAS antes del plazo (las abiertas se conservan para continuar). */
@Component
class TutorRetention implements RetentionPolicy {

    private final JdbcTemplate jdbc;

    TutorRetention(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "mensajes del profesor por texto";
    }

    @Override
    public int purgeBefore(Instant before) {
        return jdbc.update("""
                delete m from ai_messages m join ai_conversations c on c.id = m.conversation_id
                where c.status = 'CLOSED' and c.closed_at < ?""", Timestamp.from(before));
    }
}
