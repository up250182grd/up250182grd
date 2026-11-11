-- EnglishOS — V6: llamadas de voz en tiempo real (Fase 5). Ver docs/architecture/07-flujos-criticos.md §7.4.

CREATE TABLE voice_sessions (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           CHAR(26)        NOT NULL,
    user_id             BIGINT UNSIGNED NOT NULL,
    mode                VARCHAR(16)     NOT NULL,
    topic               VARCHAR(200)    NULL,
    model_id            BIGINT UNSIGNED NOT NULL,
    pricing_version_id  BIGINT UNSIGNED NOT NULL,
    policy_id           BIGINT UNSIGNED NOT NULL,
    provider            VARCHAR(32)     NOT NULL,
    provider_call_id    VARCHAR(200)    NULL,
    status              VARCHAR(16)     NOT NULL,
    end_reason          VARCHAR(32)     NULL,
    max_duration_s      INT             NOT NULL,
    reserved_mxn        DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    -- Conservado mientras la llamada está activa para impedir dos llamadas simultáneas por usuario (RF-VOC-8).
    active_user_id      BIGINT UNSIGNED NULL,
    store_transcript    BOOLEAN         NOT NULL,
    created_at          DATETIME(6)     NOT NULL,
    connected_at        DATETIME(6)     NULL,
    last_heartbeat_at   DATETIME(6)     NULL,
    last_usage_at       DATETIME(6)     NULL,
    ended_at            DATETIME(6)     NULL,
    version             INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_voice_sessions PRIMARY KEY (id),
    CONSTRAINT uq_voice_sessions_public UNIQUE (public_id),
    CONSTRAINT uq_voice_sessions_active_user UNIQUE (active_user_id),
    CONSTRAINT uq_voice_sessions_call UNIQUE (provider, provider_call_id),
    CONSTRAINT fk_voice_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_voice_sessions_model FOREIGN KEY (model_id) REFERENCES ai_models (id),
    CONSTRAINT fk_voice_sessions_pricing FOREIGN KEY (pricing_version_id) REFERENCES provider_pricing_versions (id),
    CONSTRAINT fk_voice_sessions_policy FOREIGN KEY (policy_id) REFERENCES commercial_pricing_policies (id),
    CONSTRAINT ck_voice_sessions_status CHECK (status IN ('STARTING', 'ACTIVE', 'ENDING', 'ENDED', 'FAILED')),
    CONSTRAINT ck_voice_sessions_mode CHECK (mode IN ('FREE', 'GUIDED', 'ROLEPLAY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_voice_sessions_user ON voice_sessions (user_id, created_at);
CREATE INDEX ix_voice_sessions_status ON voice_sessions (status);

-- Transcripción (solo si el alumno lo permite en su perfil). El audio NUNCA se almacena (RN-37).
CREATE TABLE voice_transcripts (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id BIGINT UNSIGNED NOT NULL,
    role       VARCHAR(16)     NOT NULL,
    content    TEXT            NOT NULL,
    created_at DATETIME(6)     NOT NULL,
    CONSTRAINT pk_voice_transcripts PRIMARY KEY (id),
    CONSTRAINT fk_voice_transcripts_session FOREIGN KEY (session_id) REFERENCES voice_sessions (id),
    CONSTRAINT ck_voice_transcripts_role CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_voice_transcripts_session ON voice_transcripts (session_id, id);

-- Proveedor de voz SIMULADO: llamada sin audio real que emite consumo y transcripción ficticios para probar
-- reservas, medidor, cortes y liquidación sin costo. Tarifas ficticias en MXN.
INSERT INTO ai_models (provider, model_code, modality, display_name, is_enabled, is_simulated, created_at)
VALUES ('FAKE', 'fake-voice-1', 'REALTIME', 'Llamada simulada (sin audio ni costo real)', TRUE, TRUE, NOW(6));

INSERT INTO provider_pricing_versions (model_id, currency, rates, effective_from, source_url, verified_at, created_at)
SELECT id, 'MXN', JSON_OBJECT('text_in', 100, 'text_in_cached', 50, 'text_out', 400, 'audio_in', 600,
                              'audio_in_cached', 50, 'audio_out', 1200),
       '2026-01-01 00:00:00', 'simulado://tarifa-ficticia-para-pruebas', '2026-11-11', NOW(6)
FROM ai_models WHERE provider = 'FAKE' AND model_code = 'fake-voice-1';
