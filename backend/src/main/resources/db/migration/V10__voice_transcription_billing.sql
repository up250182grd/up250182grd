-- Cobro de la transcripción de la voz. El proveedor factura la transcripción de lo que dice el alumno aparte, con la
-- tarifa del modelo de transcripción (no la del modelo realtime). Cada llamada fija también esa tarifa al iniciar.

ALTER TABLE ai_models DROP CHECK ck_ai_models_modality;
ALTER TABLE ai_models
    ADD CONSTRAINT ck_ai_models_modality CHECK (modality IN ('TEXT', 'REALTIME', 'TRANSCRIPTION'));

-- NULL en llamadas anteriores a esta versión (no se les cobró transcripción).
ALTER TABLE voice_sessions
    ADD COLUMN transcription_model_id           BIGINT UNSIGNED NULL AFTER policy_id,
    ADD COLUMN transcription_pricing_version_id BIGINT UNSIGNED NULL AFTER transcription_model_id,
    ADD CONSTRAINT fk_voice_sessions_tr_model FOREIGN KEY (transcription_model_id) REFERENCES ai_models (id),
    ADD CONSTRAINT fk_voice_sessions_tr_pricing
        FOREIGN KEY (transcription_pricing_version_id) REFERENCES provider_pricing_versions (id);

-- Transcripción SIMULADA para el proveedor de voz de pruebas. Tarifa ficticia en MXN por minuto de audio
-- (unidad audio_microminutes: la tarifa "por millón" de millonésimas de minuto es el precio por minuto).
INSERT INTO ai_models (provider, model_code, modality, display_name, is_enabled, is_simulated, created_at)
VALUES ('FAKE', 'fake-transcribe-1', 'TRANSCRIPTION', 'Transcripción simulada (sin costo real)', TRUE, TRUE, NOW(6));

INSERT INTO provider_pricing_versions (model_id, currency, rates, effective_from, source_url, verified_at, created_at)
SELECT id, 'MXN', JSON_OBJECT('audio_microminutes', 0.30),
       '2026-01-01 00:00:00', 'simulado://tarifa-ficticia-para-pruebas', '2026-11-12', NOW(6)
FROM ai_models WHERE provider = 'FAKE' AND model_code = 'fake-transcribe-1';
