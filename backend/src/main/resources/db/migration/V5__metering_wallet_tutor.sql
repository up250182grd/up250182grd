-- EnglishOS — V5: medición de consumo de IA, billetera (libro mayor de doble partida) y tutor por texto (Fase 4).
-- Reglas: docs/architecture/05-reglas-negocio.md §5.1–5.4. Dinero MXN DECIMAL(19,2); costos de proveedor DECIMAL(24,10).

-- ---------------------------------------------------------------------------------------------------------------
-- Catálogo de modelos y tarifas versionadas (ADR-0005). Ninguna tarifa vive en el código.
CREATE TABLE ai_models (
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider      VARCHAR(32)     NOT NULL,
    model_code    VARCHAR(100)    NOT NULL,
    modality      VARCHAR(16)     NOT NULL,
    display_name  VARCHAR(120)    NOT NULL,
    is_enabled    BOOLEAN         NOT NULL,
    is_simulated  BOOLEAN         NOT NULL,
    created_at    DATETIME(6)     NOT NULL,
    CONSTRAINT pk_ai_models PRIMARY KEY (id),
    CONSTRAINT uq_ai_models UNIQUE (provider, model_code),
    CONSTRAINT ck_ai_models_modality CHECK (modality IN ('TEXT', 'REALTIME'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- rates: precio por 1 000 000 de unidades, por tipo (text_in, text_in_cached, text_out, ...), en `currency`.
CREATE TABLE provider_pricing_versions (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    model_id       BIGINT UNSIGNED NOT NULL,
    currency       CHAR(3)         NOT NULL,
    rates          JSON            NOT NULL,
    effective_from DATETIME(6)     NOT NULL,
    source_url     VARCHAR(500)    NOT NULL,
    verified_at    DATE            NOT NULL,
    created_by     BIGINT UNSIGNED NULL,
    created_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_provider_pricing_versions PRIMARY KEY (id),
    CONSTRAINT fk_pricing_model FOREIGN KEY (model_id) REFERENCES ai_models (id),
    CONSTRAINT fk_pricing_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_pricing_currency CHECK (currency IN ('USD', 'MXN'))
) ENGINE = InnoDB;
CREATE INDEX ix_pricing_model_from ON provider_pricing_versions (model_id, effective_from);

-- Factor comercial por rol (RN-1..RN-3). Nunca se edita una versión: se crea otra con vigencia futura.
CREATE TABLE commercial_pricing_policies (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    role_code      VARCHAR(32)     NOT NULL,
    factor         DECIMAL(6, 4)   NOT NULL,
    tax_mode       VARCHAR(16)     NOT NULL,
    tax_rate       DECIMAL(6, 4)   NOT NULL,
    effective_from DATETIME(6)     NOT NULL,
    note           VARCHAR(500)    NOT NULL,
    created_by     BIGINT UNSIGNED NULL,
    created_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_commercial_pricing_policies PRIMARY KEY (id),
    CONSTRAINT fk_policies_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_policies_factor CHECK (factor >= 1 AND factor <= 5),
    CONSTRAINT ck_policies_tax_mode CHECK (tax_mode IN ('NONE', 'ADDED')),
    CONSTRAINT ck_policies_tax_rate CHECK (tax_rate >= 0 AND tax_rate < 1)
) ENGINE = InnoDB;
CREATE INDEX ix_policies_role_from ON commercial_pricing_policies (role_code, effective_from);

-- Tipos de cambio usados en cada liquidación, con su fuente (RN-14).
CREATE TABLE fx_rates (
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    base       CHAR(3)         NOT NULL,
    quote      CHAR(3)         NOT NULL,
    rate       DECIMAL(18, 6)  NOT NULL,
    source     VARCHAR(32)     NOT NULL,
    rate_date  DATE            NOT NULL,
    created_by BIGINT UNSIGNED NULL,
    created_at DATETIME(6)     NOT NULL,
    CONSTRAINT pk_fx_rates PRIMARY KEY (id),
    CONSTRAINT uq_fx_rates UNIQUE (base, quote, source, rate_date),
    CONSTRAINT fk_fx_created_by FOREIGN KEY (created_by) REFERENCES users (id),
    CONSTRAINT ck_fx_rate CHECK (rate > 0)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------------------------------------------------
-- Uso informado por el proveedor (nunca por el navegador, RN-34). Idempotente por respuesta del proveedor.
CREATE TABLE ai_usage_records (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id             BIGINT UNSIGNED NOT NULL,
    subject_type        VARCHAR(32)     NOT NULL,
    subject_id          BIGINT UNSIGNED NOT NULL,
    model_id            BIGINT UNSIGNED NOT NULL,
    pricing_version_id  BIGINT UNSIGNED NOT NULL,
    provider_request_id VARCHAR(200)    NOT NULL,
    units               JSON            NOT NULL,
    provider_cost       DECIMAL(24, 10) NOT NULL,
    provider_currency   CHAR(3)         NOT NULL,
    data_source         VARCHAR(32)     NOT NULL,
    idempotency_key     VARCHAR(200)    NOT NULL,
    occurred_at         DATETIME(6)     NOT NULL,
    CONSTRAINT pk_ai_usage_records PRIMARY KEY (id),
    CONSTRAINT uq_ai_usage_idem UNIQUE (idempotency_key),
    CONSTRAINT fk_usage_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_usage_model FOREIGN KEY (model_id) REFERENCES ai_models (id),
    CONSTRAINT fk_usage_pricing FOREIGN KEY (pricing_version_id) REFERENCES provider_pricing_versions (id),
    CONSTRAINT ck_usage_source CHECK (data_source IN ('PROVIDER_RESPONSE', 'PROVIDER_EVENT', 'USAGE_API', 'SIMULATED'))
) ENGINE = InnoDB;
CREATE INDEX ix_usage_subject ON ai_usage_records (subject_type, subject_id);
CREATE INDEX ix_usage_user_time ON ai_usage_records (user_id, occurred_at);

-- Liquidación: costo → tipo de cambio → factor → impuestos → total. Una por sujeto (conversación, llamada...).
CREATE TABLE usage_settlements (
    id                    BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id             CHAR(26)        NOT NULL,
    user_id               BIGINT UNSIGNED NOT NULL,
    role_code             VARCHAR(32)     NOT NULL,
    subject_type          VARCHAR(32)     NOT NULL,
    subject_id            BIGINT UNSIGNED NOT NULL,
    status                VARCHAR(32)     NOT NULL,
    provider_cost         DECIMAL(24, 10) NOT NULL,
    provider_currency     CHAR(3)         NOT NULL,
    fx_rate               DECIMAL(18, 6)  NULL,
    fx_source             VARCHAR(32)     NULL,
    direct_cost_mxn       DECIMAL(19, 2)  NULL,
    policy_id             BIGINT UNSIGNED NOT NULL,
    factor_applied        DECIMAL(6, 4)   NOT NULL,
    tax_mode              VARCHAR(16)     NOT NULL,
    tax_rate              DECIMAL(6, 4)   NOT NULL,
    price_before_tax_mxn  DECIMAL(19, 2)  NULL,
    markup_mxn            DECIMAL(19, 2)  NULL,
    tax_mxn               DECIMAL(19, 2)  NULL,
    total_mxn             DECIMAL(19, 2)  NULL,
    ledger_transaction_id BIGINT UNSIGNED NULL,
    note                  VARCHAR(500)    NULL,
    created_at            DATETIME(6)     NOT NULL,
    settled_at            DATETIME(6)     NULL,
    CONSTRAINT pk_usage_settlements PRIMARY KEY (id),
    CONSTRAINT uq_usage_settlements_public UNIQUE (public_id),
    CONSTRAINT uq_usage_settlements_subject UNIQUE (subject_type, subject_id),
    CONSTRAINT fk_settlement_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_settlement_policy FOREIGN KEY (policy_id) REFERENCES commercial_pricing_policies (id),
    CONSTRAINT ck_settlement_status CHECK (status IN ('SETTLED', 'PENDING_RECONCILIATION', 'RECONCILED'))
) ENGINE = InnoDB;
CREATE INDEX ix_settlements_user_time ON usage_settlements (user_id, settled_at);

-- ---------------------------------------------------------------------------------------------------------------
-- Libro mayor de doble partida (ADR-0003). owner_key = id del usuario, o 0 para cuentas del negocio.
CREATE TABLE ledger_accounts (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    code           VARCHAR(40)     NOT NULL,
    owner_key      BIGINT UNSIGNED NOT NULL,
    currency       CHAR(3)         NOT NULL,
    balance        DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    created_at     DATETIME(6)     NOT NULL,
    version        INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_ledger_accounts PRIMARY KEY (id),
    CONSTRAINT uq_ledger_accounts UNIQUE (code, owner_key, currency)
) ENGINE = InnoDB;

CREATE TABLE ledger_transactions (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id       CHAR(26)        NOT NULL,
    type            VARCHAR(32)     NOT NULL,
    user_id         BIGINT UNSIGNED NULL,
    idempotency_key VARCHAR(120)    NOT NULL,
    description     VARCHAR(300)    NOT NULL,
    ref_type        VARCHAR(32)     NULL,
    ref_id          VARCHAR(64)     NULL,
    created_by      BIGINT UNSIGNED NULL,
    created_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_ledger_transactions PRIMARY KEY (id),
    CONSTRAINT uq_ledger_tx_public UNIQUE (public_id),
    CONSTRAINT uq_ledger_tx_idem UNIQUE (idempotency_key),
    CONSTRAINT fk_ledger_tx_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_ledger_tx_type CHECK (type IN ('TOPUP', 'RESERVE', 'CAPTURE', 'RELEASE', 'INTERNAL_USAGE',
                                                 'ADJUSTMENT', 'REFUND'))
) ENGINE = InnoDB;
CREATE INDEX ix_ledger_tx_user_time ON ledger_transactions (user_id, created_at);

CREATE TABLE ledger_entries (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    transaction_id BIGINT UNSIGNED NOT NULL,
    account_id     BIGINT UNSIGNED NOT NULL,
    amount         DECIMAL(19, 2)  NOT NULL,
    created_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_ledger_entries PRIMARY KEY (id),
    CONSTRAINT fk_ledger_entries_tx FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES ledger_accounts (id),
    CONSTRAINT ck_ledger_entries_nonzero CHECK (amount <> 0)
) ENGINE = InnoDB;
CREATE INDEX ix_ledger_entries_account ON ledger_entries (account_id, id);

-- Reservas de saldo por sesión de IA (RN-22..RN-25).
CREATE TABLE wallet_reservations (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    subject_type    VARCHAR(32)     NOT NULL,
    subject_id      BIGINT UNSIGNED NOT NULL,
    amount_reserved DECIMAL(19, 2)  NOT NULL,
    amount_captured DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    amount_released DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    status          VARCHAR(16)     NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    updated_at      DATETIME(6)     NOT NULL,
    version         INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_wallet_reservations PRIMARY KEY (id),
    CONSTRAINT uq_wallet_reservations_subject UNIQUE (subject_type, subject_id),
    CONSTRAINT fk_wallet_reservations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_wallet_reservations_status CHECK (status IN ('ACTIVE', 'SETTLED'))
) ENGINE = InnoDB;
CREATE INDEX ix_wallet_reservations_user ON wallet_reservations (user_id, status);

-- Preferencias de gasto del usuario (RN-26, RN-27).
CREATE TABLE wallet_preferences (
    user_id                    BIGINT UNSIGNED NOT NULL,
    monthly_limit_mxn          DECIMAL(19, 2)  NULL,
    low_balance_threshold_mxn  DECIMAL(19, 2)  NOT NULL,
    updated_at                 DATETIME(6)     NOT NULL,
    CONSTRAINT pk_wallet_preferences PRIMARY KEY (user_id),
    CONSTRAINT fk_wallet_preferences_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_wallet_preferences_limit CHECK (monthly_limit_mxn IS NULL OR monthly_limit_mxn >= 0)
) ENGINE = InnoDB;

CREATE TABLE billing_incidents (
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    type        VARCHAR(40)     NOT NULL,
    user_id     BIGINT UNSIGNED NULL,
    ref_type    VARCHAR(32)     NULL,
    ref_id      VARCHAR(64)     NULL,
    description VARCHAR(500)    NOT NULL,
    status      VARCHAR(16)     NOT NULL,
    created_at  DATETIME(6)     NOT NULL,
    resolved_at DATETIME(6)     NULL,
    CONSTRAINT pk_billing_incidents PRIMARY KEY (id),
    CONSTRAINT fk_billing_incidents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_billing_incidents_status CHECK (status IN ('OPEN', 'RESOLVED'))
) ENGINE = InnoDB;
CREATE INDEX ix_billing_incidents_status ON billing_incidents (status, created_at);

-- ---------------------------------------------------------------------------------------------------------------
-- Tutor por texto.
CREATE TABLE ai_conversations (
    id                 BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id          CHAR(26)        NOT NULL,
    user_id            BIGINT UNSIGNED NOT NULL,
    channel            VARCHAR(16)     NOT NULL,
    mode               VARCHAR(16)     NOT NULL,
    topic              VARCHAR(200)    NULL,
    prompt_version     VARCHAR(40)     NOT NULL,
    model_id           BIGINT UNSIGNED NOT NULL,
    pricing_version_id BIGINT UNSIGNED NOT NULL,
    policy_id          BIGINT UNSIGNED NOT NULL,
    status             VARCHAR(16)     NOT NULL,
    turns              INT             NOT NULL DEFAULT 0,
    started_at         DATETIME(6)     NOT NULL,
    last_activity_at   DATETIME(6)     NOT NULL,
    closed_at          DATETIME(6)     NULL,
    close_reason       VARCHAR(32)     NULL,
    version            INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_ai_conversations PRIMARY KEY (id),
    CONSTRAINT uq_ai_conversations_public UNIQUE (public_id),
    CONSTRAINT fk_ai_conversations_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_ai_conversations_model FOREIGN KEY (model_id) REFERENCES ai_models (id),
    CONSTRAINT fk_ai_conversations_pricing FOREIGN KEY (pricing_version_id) REFERENCES provider_pricing_versions (id),
    CONSTRAINT fk_ai_conversations_policy FOREIGN KEY (policy_id) REFERENCES commercial_pricing_policies (id),
    CONSTRAINT ck_ai_conversations_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT ck_ai_conversations_mode CHECK (mode IN ('FREE', 'GUIDED', 'ROLEPLAY'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_ai_conversations_user ON ai_conversations (user_id, started_at);
CREATE INDEX ix_ai_conversations_open ON ai_conversations (status, last_activity_at);

CREATE TABLE ai_messages (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    conversation_id BIGINT UNSIGNED NOT NULL,
    role            VARCHAR(16)     NOT NULL,
    content         TEXT            NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_ai_messages PRIMARY KEY (id),
    CONSTRAINT fk_ai_messages_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id),
    CONSTRAINT ck_ai_messages_role CHECK (role IN ('USER', 'ASSISTANT'))
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_ai_messages_conversation ON ai_messages (conversation_id, id);

CREATE TABLE learner_errors (
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id         BIGINT UNSIGNED NOT NULL,
    conversation_id BIGINT UNSIGNED NOT NULL,
    original        VARCHAR(500)    NOT NULL,
    correction      VARCHAR(500)    NOT NULL,
    explanation_es  VARCHAR(1000)   NOT NULL,
    explanation_en  VARCHAR(1000)   NOT NULL,
    category        VARCHAR(40)     NOT NULL,
    created_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_learner_errors PRIMARY KEY (id),
    CONSTRAINT fk_learner_errors_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT fk_learner_errors_conversation FOREIGN KEY (conversation_id) REFERENCES ai_conversations (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_learner_errors_user ON learner_errors (user_id, category);

-- ---------------------------------------------------------------------------------------------------------------
-- Datos iniciales.

-- Factores comerciales obligatorios (RN-1). Impuestos en modo NONE hasta la decisión fiscal D-1.
INSERT INTO commercial_pricing_policies (role_code, factor, tax_mode, tax_rate, effective_from, note, created_at) VALUES
    ('ADMIN', 1.0000, 'NONE', 0.0000, '2026-01-01 00:00:00', 'Valor inicial: administrador al costo (recargo 0 %).', NOW(6)),
    ('STUDENT', 1.3000, 'NONE', 0.0000, '2026-01-01 00:00:00', 'Valor inicial: costo + 30 % de recargo comercial.', NOW(6));

-- Proveedor SIMULADO: permite usar y probar todo el flujo sin gastar dinero real. Sus tarifas son ficticias,
-- están en MXN (sin tipo de cambio) y se etiquetan como simuladas en toda la interfaz.
INSERT INTO ai_models (provider, model_code, modality, display_name, is_enabled, is_simulated, created_at)
VALUES ('FAKE', 'fake-tutor-1', 'TEXT', 'Profesor simulado (sin costo real)', TRUE, TRUE, NOW(6));

INSERT INTO provider_pricing_versions (model_id, currency, rates, effective_from, source_url, verified_at, created_at)
SELECT id, 'MXN', JSON_OBJECT('text_in', 100, 'text_in_cached', 50, 'text_out', 400), '2026-01-01 00:00:00',
       'simulado://tarifa-ficticia-para-pruebas', '2026-11-11', NOW(6)
FROM ai_models WHERE provider = 'FAKE' AND model_code = 'fake-tutor-1';

INSERT INTO permissions (code, description) VALUES
    ('pricing:write', 'Configurar modelos, tarifas del proveedor, tipos de cambio y factores comerciales'),
    ('billing:read:any', 'Consultar consumo, liquidaciones, billeteras e ingresos de cualquier usuario');

INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code IN ('pricing:write', 'billing:read:any')
WHERE r.code = 'ADMIN';
