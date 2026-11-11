-- Fase 6: vocabulario con repetición espaciada (SM-2), práctica de errores recurrentes y recomendaciones por reglas.

-- Catálogo global de términos (se alimenta del vocabulario de las lecciones publicadas).
CREATE TABLE vocabulary_items (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    term            VARCHAR(120)    NOT NULL,
    part_of_speech  VARCHAR(24)     NOT NULL,
    ipa             VARCHAR(120)    NULL,
    translation_es  VARCHAR(255)    NOT NULL,
    cefr_level      VARCHAR(2)      NOT NULL,
    source_lesson   CHAR(26)        NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_vocabulary_items PRIMARY KEY (id),
    CONSTRAINT uq_vocabulary_items_term UNIQUE (term, part_of_speech),
    CONSTRAINT ck_vocabulary_items_level CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Estado SM-2 por alumno y término (RF-SRS-1..4). El factor de facilidad se guarda como decimal exacto.
CREATE TABLE user_vocabulary_cards (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id          CHAR(26)        NOT NULL,
    user_id            BIGINT UNSIGNED NOT NULL,
    item_id            BIGINT UNSIGNED NOT NULL,
    algorithm          VARCHAR(16)     NOT NULL,
    algorithm_version  INT             NOT NULL,
    ease_factor        DECIMAL(5, 3)   NOT NULL,
    repetitions        INT             NOT NULL,
    interval_days      INT             NOT NULL,
    learning_state     VARCHAR(16)     NOT NULL,
    due_at             DATETIME(6)     NOT NULL,
    lapses             INT             NOT NULL DEFAULT 0,
    review_count       INT             NOT NULL DEFAULT 0,
    first_reviewed_at  DATETIME(6)     NULL,
    last_reviewed_at   DATETIME(6)     NULL,
    created_at         DATETIME(6)     NOT NULL,
    version            INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_vocabulary_cards PRIMARY KEY (id),
    CONSTRAINT uq_user_vocabulary_cards_public UNIQUE (public_id),
    CONSTRAINT uq_user_vocabulary_cards_item UNIQUE (user_id, item_id),
    CONSTRAINT fk_user_vocabulary_cards_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_vocabulary_cards_item FOREIGN KEY (item_id) REFERENCES vocabulary_items (id),
    CONSTRAINT ck_user_vocabulary_cards_state CHECK (learning_state IN ('NEW', 'LEARNING', 'REVIEW', 'RELEARNING')),
    CONSTRAINT ck_user_vocabulary_cards_ef CHECK (ease_factor >= 1.300)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_user_vocabulary_cards_due ON user_vocabulary_cards (user_id, due_at);

CREATE TABLE vocabulary_reviews (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    card_id           BIGINT UNSIGNED NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    client_review_id  VARCHAR(64)     NOT NULL,
    quality           INT             NOT NULL,
    state_before      JSON            NOT NULL,
    state_after       JSON            NOT NULL,
    duration_ms       INT             NULL,
    reviewed_at       DATETIME(6)     NOT NULL,
    CONSTRAINT pk_vocabulary_reviews PRIMARY KEY (id),
    CONSTRAINT uq_vocabulary_reviews_client UNIQUE (user_id, client_review_id),
    CONSTRAINT fk_vocabulary_reviews_card FOREIGN KEY (card_id) REFERENCES user_vocabulary_cards (id),
    CONSTRAINT ck_vocabulary_reviews_quality CHECK (quality BETWEEN 0 AND 5)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_vocabulary_reviews_card ON vocabulary_reviews (card_id, reviewed_at);
CREATE INDEX ix_vocabulary_reviews_user ON vocabulary_reviews (user_id, reviewed_at);

-- Práctica de errores del profesor IA: se considera superado tras 2 aciertos seguidos.
ALTER TABLE learner_errors
    ADD COLUMN public_id        CHAR(26)    NULL AFTER id,
    ADD COLUMN practice_count   INT         NOT NULL DEFAULT 0,
    ADD COLUMN correct_streak   INT         NOT NULL DEFAULT 0,
    ADD COLUMN last_practiced_at DATETIME(6) NULL,
    ADD COLUMN resolved_at      DATETIME(6) NULL;
UPDATE learner_errors SET public_id = LPAD(CONV(id, 10, 36), 26, '0') WHERE public_id IS NULL;
ALTER TABLE learner_errors MODIFY public_id CHAR(26) NOT NULL, ADD CONSTRAINT uq_learner_errors_public UNIQUE (public_id);

-- Pesos del recomendador, versionados (RF-REC-2): la versión vigente es la más reciente.
CREATE TABLE recommendation_weights (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    weights     JSON            NOT NULL,
    note        VARCHAR(500)    NOT NULL,
    created_by  BIGINT UNSIGNED NULL,
    created_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_recommendation_weights PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

INSERT INTO recommendation_weights (weights, note, created_by, created_at) VALUES (
    JSON_OBJECT('diagnostic', 100, 'reviews', 80, 'nextLesson', 70, 'recurringErrors', 60, 'weakSkill', 45,
                'conversation', 35),
    'Valores iniciales.', NULL, NOW(6));

INSERT INTO permissions (code, description) VALUES
    ('recommendation:write', 'Configurar los pesos del motor de recomendaciones');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'recommendation:write' WHERE r.code = 'ADMIN';
