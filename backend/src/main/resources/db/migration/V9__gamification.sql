-- Fase 8: gamificación (XP, rachas, insignias). Se alimenta de eventos de dominio de los demás módulos.

-- Cada evento se registra una sola vez (idempotencia): solo si se inserta se suman XP y contadores.
CREATE TABLE game_events (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id      BIGINT UNSIGNED NOT NULL,
    source_type  VARCHAR(32)     NOT NULL,
    source_id    VARCHAR(100)    NOT NULL,
    ref          VARCHAR(64)     NULL,
    local_date   DATE            NOT NULL,
    xp           INT             NOT NULL,
    created_at   DATETIME(6)     NOT NULL,
    CONSTRAINT pk_game_events PRIMARY KEY (id),
    CONSTRAINT uq_game_events_source UNIQUE (user_id, source_type, source_id),
    CONSTRAINT fk_game_events_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_game_events_xp CHECK (xp >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_game_events_user_date ON game_events (user_id, local_date);

-- Actividad por día local del alumno (base de la racha).
CREATE TABLE daily_activity (
    user_id         BIGINT UNSIGNED NOT NULL,
    local_date      DATE            NOT NULL,
    activity_ms     BIGINT          NOT NULL DEFAULT 0,
    lessons         INT             NOT NULL DEFAULT 0,
    reviews         INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_daily_activity PRIMARY KEY (user_id, local_date),
    CONSTRAINT fk_daily_activity_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

-- Contadores de logros y XP acumulado.
CREATE TABLE user_game_stats (
    user_id            BIGINT UNSIGNED NOT NULL,
    xp_total           BIGINT          NOT NULL DEFAULT 0,
    lessons_completed  INT             NOT NULL DEFAULT 0,
    lessons_mastered   INT             NOT NULL DEFAULT 0,
    words_learned      INT             NOT NULL DEFAULT 0,
    errors_resolved    INT             NOT NULL DEFAULT 0,
    conversations      INT             NOT NULL DEFAULT 0,
    diagnostic_done    BOOLEAN         NOT NULL DEFAULT FALSE,
    best_streak        INT             NOT NULL DEFAULT 0,
    updated_at         DATETIME(6)     NOT NULL,
    CONSTRAINT pk_user_game_stats PRIMARY KEY (user_id),
    CONSTRAINT fk_user_game_stats_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;

CREATE TABLE user_badges (
    user_id     BIGINT UNSIGNED NOT NULL,
    code        VARCHAR(40)     NOT NULL,
    awarded_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_user_badges PRIMARY KEY (user_id, code),
    CONSTRAINT fk_user_badges_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
