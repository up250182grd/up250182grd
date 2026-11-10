-- EnglishOS — V1: identidad, roles, permisos, consentimientos, tokens de un solo uso.
-- Ver docs/architecture/06-modelo-datos.md §6.3 (identity).

CREATE TABLE users (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           CHAR(26)        NOT NULL,
    email               VARCHAR(254)    NOT NULL,
    email_verified_at   DATETIME(6)     NULL,
    password_hash       VARCHAR(255)    NOT NULL,
    status              VARCHAR(32)     NOT NULL,
    failed_login_count  INT             NOT NULL DEFAULT 0,
    locked_until        DATETIME(6)     NULL,
    last_login_at       DATETIME(6)     NULL,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    version             INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_users PRIMARY KEY (id),
    CONSTRAINT uq_users_public_id UNIQUE (public_id),
    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT ck_users_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'DELETED')),
    CONSTRAINT ck_users_failed_login_count CHECK (failed_login_count >= 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_profiles (
    user_id                     BIGINT UNSIGNED NOT NULL,
    display_name                VARCHAR(80)     NOT NULL,
    explanation_language        VARCHAR(8)      NOT NULL DEFAULT 'es',
    timezone                    VARCHAR(64)     NOT NULL DEFAULT 'America/Mexico_City',
    daily_goal_minutes          INT             NOT NULL DEFAULT 20,
    store_transcripts           BOOLEAN         NOT NULL DEFAULT TRUE,
    onboarding_completed        BOOLEAN         NOT NULL DEFAULT FALSE,
    created_at                  DATETIME(6)     NOT NULL,
    updated_at                  DATETIME(6)     NOT NULL,
    version                     INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_user_profiles PRIMARY KEY (user_id),
    CONSTRAINT fk_user_profiles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_user_profiles_lang CHECK (explanation_language IN ('es', 'en')),
    CONSTRAINT ck_user_profiles_goal CHECK (daily_goal_minutes BETWEEN 5 AND 240)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE roles (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(32)     NOT NULL,
    description VARCHAR(255)    NOT NULL,
    CONSTRAINT pk_roles PRIMARY KEY (id),
    CONSTRAINT uq_roles_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE permissions (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code        VARCHAR(64)     NOT NULL,
    description VARCHAR(255)    NOT NULL,
    CONSTRAINT pk_permissions PRIMARY KEY (id),
    CONSTRAINT uq_permissions_code UNIQUE (code)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE user_roles (
    user_id BIGINT UNSIGNED NOT NULL,
    role_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_user_roles PRIMARY KEY (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB;

CREATE TABLE role_permissions (
    role_id       BIGINT UNSIGNED NOT NULL,
    permission_id BIGINT UNSIGNED NOT NULL,
    CONSTRAINT pk_role_permissions PRIMARY KEY (role_id, permission_id),
    CONSTRAINT fk_role_permissions_role FOREIGN KEY (role_id) REFERENCES roles (id),
    CONSTRAINT fk_role_permissions_perm FOREIGN KEY (permission_id) REFERENCES permissions (id)
) ENGINE = InnoDB;

CREATE TABLE consents (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id          BIGINT UNSIGNED NOT NULL,
    document_type    VARCHAR(32)     NOT NULL,
    document_version VARCHAR(32)     NOT NULL,
    accepted_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_consents PRIMARY KEY (id),
    CONSTRAINT fk_consents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_consents_type CHECK (document_type IN ('TERMS', 'PRIVACY', 'TRANSCRIPTS'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_consents_user_type ON consents (user_id, document_type);

-- Tokens de un solo uso (verificación de correo, restablecimiento de contraseña).
-- Solo se almacena el hash SHA-256 del token; el valor en claro viaja únicamente por correo.
CREATE TABLE auth_tokens (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    purpose     VARCHAR(32)     NOT NULL,
    token_hash  CHAR(64)        NOT NULL,
    expires_at  DATETIME(6)     NOT NULL,
    used_at     DATETIME(6)     NULL,
    created_at  DATETIME(6)     NOT NULL,
    CONSTRAINT pk_auth_tokens PRIMARY KEY (id),
    CONSTRAINT uq_auth_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_auth_tokens_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_auth_tokens_purpose CHECK (purpose IN ('EMAIL_VERIFY', 'PASSWORD_RESET'))
) ENGINE = InnoDB;
CREATE INDEX ix_auth_tokens_user_purpose ON auth_tokens (user_id, purpose);

-- Datos semilla: roles y permisos. Los permisos se definen en migraciones, no desde la UI.
INSERT INTO roles (code, description) VALUES
    ('ADMIN', 'Propietario/administrador del sistema. Consumo de IA al costo (factor 1.00).'),
    ('STUDENT', 'Estudiante. Consumo de IA con recargo comercial (factor 1.30).');

INSERT INTO permissions (code, description) VALUES
    ('user:read:any', 'Consultar cualquier usuario'),
    ('user:suspend', 'Suspender o reactivar cuentas'),
    ('user:role:write', 'Asignar o retirar roles'),
    ('audit:read', 'Consultar la bitácora de auditoría');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r CROSS JOIN permissions p WHERE r.code = 'ADMIN';
