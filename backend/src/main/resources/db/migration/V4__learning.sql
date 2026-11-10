-- EnglishOS — V4: sistema educativo (Fase 3).
-- Textos pedagógicos bilingües: columnas *_es / *_en. Ver docs/architecture/06-modelo-datos.md.
-- Estados de contenido: DRAFT, PUBLISHED, ARCHIVED. `reviewed_at` NULL = contenido aún no revisado por una persona.

CREATE TABLE courses (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id      CHAR(26)        NOT NULL,
    slug           VARCHAR(80)     NOT NULL,
    cefr_level     VARCHAR(2)      NOT NULL,
    title_es       VARCHAR(160)    NOT NULL,
    title_en       VARCHAR(160)    NOT NULL,
    description_es TEXT            NOT NULL,
    description_en TEXT            NOT NULL,
    status         VARCHAR(16)     NOT NULL,
    sort_order     INT             NOT NULL,
    reviewed_at    DATETIME(6)     NULL,
    created_at     DATETIME(6)     NOT NULL,
    updated_at     DATETIME(6)     NOT NULL,
    version        INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_courses PRIMARY KEY (id),
    CONSTRAINT uq_courses_public_id UNIQUE (public_id),
    CONSTRAINT uq_courses_slug UNIQUE (slug),
    CONSTRAINT ck_courses_level CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT ck_courses_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE course_modules (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id    CHAR(26)        NOT NULL,
    course_id    BIGINT UNSIGNED NOT NULL,
    slug         VARCHAR(80)     NOT NULL,
    title_es     VARCHAR(160)    NOT NULL,
    title_en     VARCHAR(160)    NOT NULL,
    objective_es TEXT            NOT NULL,
    objective_en TEXT            NOT NULL,
    status       VARCHAR(16)     NOT NULL,
    sort_order   INT             NOT NULL,
    created_at   DATETIME(6)     NOT NULL,
    updated_at   DATETIME(6)     NOT NULL,
    version      INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_course_modules PRIMARY KEY (id),
    CONSTRAINT uq_course_modules_public_id UNIQUE (public_id),
    CONSTRAINT uq_course_modules_slug UNIQUE (slug),
    CONSTRAINT fk_course_modules_course FOREIGN KEY (course_id) REFERENCES courses (id),
    CONSTRAINT ck_course_modules_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_course_modules_course ON course_modules (course_id, sort_order);

CREATE TABLE lessons (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id         CHAR(26)        NOT NULL,
    module_id         BIGINT UNSIGNED NOT NULL,
    slug              VARCHAR(80)     NOT NULL,
    cefr_level        VARCHAR(2)      NOT NULL,
    title_es          VARCHAR(160)    NOT NULL,
    title_en          VARCHAR(160)    NOT NULL,
    objective_es      TEXT            NOT NULL,
    objective_en      TEXT            NOT NULL,
    explanation_es    MEDIUMTEXT      NOT NULL,
    explanation_en    MEDIUMTEXT      NOT NULL,
    examples          JSON            NOT NULL,
    vocabulary        JSON            NOT NULL,
    estimated_minutes INT             NOT NULL,
    status            VARCHAR(16)     NOT NULL,
    sort_order        INT             NOT NULL,
    content_version   INT             NOT NULL DEFAULT 1,
    reviewed_at       DATETIME(6)     NULL,
    created_at        DATETIME(6)     NOT NULL,
    updated_at        DATETIME(6)     NOT NULL,
    version           INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_lessons PRIMARY KEY (id),
    CONSTRAINT uq_lessons_public_id UNIQUE (public_id),
    CONSTRAINT uq_lessons_slug UNIQUE (slug),
    CONSTRAINT fk_lessons_module FOREIGN KEY (module_id) REFERENCES course_modules (id),
    CONSTRAINT ck_lessons_level CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT ck_lessons_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT ck_lessons_minutes CHECK (estimated_minutes BETWEEN 1 AND 180)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_lessons_module ON lessons (module_id, sort_order);

CREATE TABLE lesson_prerequisites (
    lesson_id              BIGINT UNSIGNED NOT NULL,
    prerequisite_lesson_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_lesson_prerequisites PRIMARY KEY (lesson_id, prerequisite_lesson_id),
    CONSTRAINT fk_lesson_prereq_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT fk_lesson_prereq_prereq FOREIGN KEY (prerequisite_lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_lesson_prereq_self CHECK (lesson_id <> prerequisite_lesson_id)
) ENGINE = InnoDB;

-- Ejercicios de lecciones (purpose = LESSON) y banco del diagnóstico (purpose = DIAGNOSTIC, lesson_id NULL).
-- `solution` NUNCA se serializa hacia el alumno antes de que responda.
CREATE TABLE exercises (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id        CHAR(26)        NOT NULL,
    slug             VARCHAR(80)     NOT NULL,
    purpose          VARCHAR(16)     NOT NULL,
    lesson_id        BIGINT UNSIGNED NULL,
    type             VARCHAR(32)     NOT NULL,
    skill            VARCHAR(16)     NOT NULL,
    cefr_level       VARCHAR(2)      NOT NULL,
    instructions_es  VARCHAR(500)    NOT NULL,
    instructions_en  VARCHAR(500)    NOT NULL,
    prompt           JSON            NOT NULL,
    solution         JSON            NOT NULL,
    feedback_es      TEXT            NOT NULL,
    feedback_en      TEXT            NOT NULL,
    hint_es          VARCHAR(500)    NULL,
    hint_en          VARCHAR(500)    NULL,
    status           VARCHAR(16)     NOT NULL,
    sort_order       INT             NOT NULL,
    created_at       DATETIME(6)     NOT NULL,
    updated_at       DATETIME(6)     NOT NULL,
    version          INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_exercises PRIMARY KEY (id),
    CONSTRAINT uq_exercises_public_id UNIQUE (public_id),
    CONSTRAINT uq_exercises_slug UNIQUE (slug),
    CONSTRAINT fk_exercises_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_exercises_purpose CHECK (purpose IN ('LESSON', 'DIAGNOSTIC')),
    CONSTRAINT ck_exercises_purpose_lesson CHECK ((purpose = 'LESSON') = (lesson_id IS NOT NULL)),
    CONSTRAINT ck_exercises_type CHECK (type IN ('MULTIPLE_CHOICE', 'TRUE_FALSE', 'FILL_BLANK', 'WORD_ORDER',
                                                 'MATCHING', 'ERROR_SPOTTING')),
    CONSTRAINT ck_exercises_skill CHECK (skill IN ('VOCABULARY', 'GRAMMAR', 'READING')),
    CONSTRAINT ck_exercises_level CHECK (cefr_level IN ('A1', 'A2', 'B1', 'B2', 'C1', 'C2')),
    CONSTRAINT ck_exercises_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_exercises_lesson ON exercises (lesson_id, sort_order);
CREATE INDEX ix_exercises_bank ON exercises (purpose, skill, cefr_level, status);

-- Una "ejecución" de lección: desde que el alumno la empieza hasta que la termina. Su puntaje alimenta el dominio.
CREATE TABLE lesson_runs (
    id           BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id    CHAR(26)        NOT NULL,
    user_id      BIGINT UNSIGNED NOT NULL,
    lesson_id    BIGINT UNSIGNED NOT NULL,
    started_at   DATETIME(6)     NOT NULL,
    completed_at DATETIME(6)     NULL,
    local_date   DATE            NULL,
    score        DECIMAL(5, 4)   NULL,
    CONSTRAINT pk_lesson_runs PRIMARY KEY (id),
    CONSTRAINT uq_lesson_runs_public_id UNIQUE (public_id),
    CONSTRAINT fk_lesson_runs_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_runs_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_lesson_runs_score CHECK (score IS NULL OR (score >= 0 AND score <= 1))
) ENGINE = InnoDB;
CREATE INDEX ix_lesson_runs_user_lesson ON lesson_runs (user_id, lesson_id, completed_at);

CREATE TABLE exercise_attempts (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    exercise_id     BIGINT UNSIGNED NOT NULL,
    lesson_run_id   BIGINT UNSIGNED NULL,
    answer          JSON            NOT NULL,
    is_correct      BOOLEAN         NOT NULL,
    score           DECIMAL(5, 4)   NOT NULL,
    evaluation      VARCHAR(16)     NOT NULL,
    hints_used      INT             NOT NULL DEFAULT 0,
    duration_ms     INT             NULL,
    idempotency_key VARCHAR(64)     NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_exercise_attempts PRIMARY KEY (id),
    CONSTRAINT uq_exercise_attempts_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT fk_exercise_attempts_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_exercise_attempts_exercise FOREIGN KEY (exercise_id) REFERENCES exercises (id),
    CONSTRAINT fk_exercise_attempts_run FOREIGN KEY (lesson_run_id) REFERENCES lesson_runs (id),
    CONSTRAINT ck_exercise_attempts_eval CHECK (evaluation IN ('RULE', 'AI')),
    CONSTRAINT ck_exercise_attempts_score CHECK (score >= 0 AND score <= 1),
    CONSTRAINT ck_exercise_attempts_hints CHECK (hints_used >= 0)
) ENGINE = InnoDB;
CREATE INDEX ix_exercise_attempts_user_exercise ON exercise_attempts (user_id, exercise_id, created_at);
CREATE INDEX ix_exercise_attempts_run ON exercise_attempts (lesson_run_id, exercise_id, id);

CREATE TABLE lesson_progress (
    user_id         BIGINT UNSIGNED NOT NULL,
    lesson_id       BIGINT UNSIGNED NOT NULL,
    state           VARCHAR(16)     NOT NULL,
    best_score      DECIMAL(5, 4)   NULL,
    last_score      DECIMAL(5, 4)   NULL,
    completed_runs  INT             NOT NULL DEFAULT 0,
    last_activity_at DATETIME(6)    NOT NULL,
    mastered_at     DATETIME(6)     NULL,
    version         INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_lesson_progress PRIMARY KEY (user_id, lesson_id),
    CONSTRAINT fk_lesson_progress_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_lesson_progress_lesson FOREIGN KEY (lesson_id) REFERENCES lessons (id),
    CONSTRAINT ck_lesson_progress_state CHECK (state IN ('IN_PROGRESS', 'PRACTICED', 'MASTERED'))
) ENGINE = InnoDB;

-- Diagnóstico adaptativo.
CREATE TABLE assessment_sessions (
    id                BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id         CHAR(26)        NOT NULL,
    user_id           BIGINT UNSIGNED NOT NULL,
    status            VARCHAR(16)     NOT NULL,
    algorithm_version VARCHAR(32)     NOT NULL,
    state             JSON            NOT NULL,
    current_item_id   BIGINT UNSIGNED NULL,
    started_at        DATETIME(6)     NOT NULL,
    completed_at      DATETIME(6)     NULL,
    result            JSON            NULL,
    version           INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_assessment_sessions PRIMARY KEY (id),
    CONSTRAINT uq_assessment_sessions_public_id UNIQUE (public_id),
    CONSTRAINT fk_assessment_sessions_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_assessment_sessions_item FOREIGN KEY (current_item_id) REFERENCES exercises (id),
    CONSTRAINT ck_assessment_sessions_status CHECK (status IN ('IN_PROGRESS', 'COMPLETED', 'ABANDONED'))
) ENGINE = InnoDB;
CREATE INDEX ix_assessment_sessions_user ON assessment_sessions (user_id, status);

CREATE TABLE assessment_responses (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    session_id     BIGINT UNSIGNED NOT NULL,
    item_id        BIGINT UNSIGNED NOT NULL,
    skill          VARCHAR(16)     NOT NULL,
    item_level     VARCHAR(2)      NOT NULL,
    answer         JSON            NOT NULL,
    is_correct     BOOLEAN         NOT NULL,
    created_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_assessment_responses PRIMARY KEY (id),
    CONSTRAINT uq_assessment_responses_item UNIQUE (session_id, item_id),
    CONSTRAINT fk_assessment_responses_session FOREIGN KEY (session_id) REFERENCES assessment_sessions (id),
    CONSTRAINT fk_assessment_responses_item FOREIGN KEY (item_id) REFERENCES exercises (id)
) ENGINE = InnoDB;

CREATE TABLE user_skill_levels (
    user_id         BIGINT UNSIGNED NOT NULL,
    skill           VARCHAR(16)     NOT NULL,
    estimated_level VARCHAR(2)      NOT NULL,
    confidence      VARCHAR(8)      NOT NULL,
    evidence_count  INT             NOT NULL,
    source          VARCHAR(16)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_user_skill_levels PRIMARY KEY (user_id, skill),
    CONSTRAINT fk_user_skill_levels_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_skill_levels_conf CHECK (confidence IN ('LOW', 'MEDIUM', 'HIGH'))
) ENGINE = InnoDB;

CREATE TABLE learning_plans (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    source      VARCHAR(16)     NOT NULL,
    start_level VARCHAR(2)      NOT NULL,
    course_ids  JSON            NOT NULL,
    is_active   BOOLEAN         NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_learning_plans PRIMARY KEY (id),
    CONSTRAINT fk_learning_plans_user FOREIGN KEY (user_id) REFERENCES users (id)
) ENGINE = InnoDB;
CREATE INDEX ix_learning_plans_user_active ON learning_plans (user_id, is_active);

-- Permisos de gestión de contenido para el administrador.
INSERT INTO permissions (code, description) VALUES
    ('content:read:any', 'Ver contenido en cualquier estado (borradores incluidos)'),
    ('content:write', 'Crear, editar, ordenar, publicar y archivar contenido educativo');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('content:read:any', 'content:write')
WHERE r.code = 'ADMIN';
