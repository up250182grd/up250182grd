package com.englishos.voice.infrastructure;

import java.sql.Timestamp;
import java.time.Instant;

import com.englishos.shared.privacy.RetentionPolicy;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Borra las transcripciones de llamadas antes del plazo. El audio nunca se guarda. */
@Component
class VoiceRetention implements RetentionPolicy {

    private final JdbcTemplate jdbc;

    VoiceRetention(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public String name() {
        return "transcripciones de voz";
    }

    @Override
    public int purgeBefore(Instant before) {
        return jdbc.update("delete from voice_transcripts where created_at < ?", Timestamp.from(before));
    }
}
