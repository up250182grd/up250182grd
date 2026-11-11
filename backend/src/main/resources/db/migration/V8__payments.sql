-- Fase 7: recargas con pasarela (Mercado Pago, modo prueba), webhooks, devoluciones manuales e incidencias resueltas.

-- Intención de recarga: el monto lo fija el servidor al crearla; el pago del proveedor debe coincidir exactamente.
CREATE TABLE payment_intents (
    id                     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id              CHAR(26)        NOT NULL,
    user_id                BIGINT UNSIGNED NOT NULL,
    provider               VARCHAR(24)     NOT NULL,
    amount_mxn             DECIMAL(19, 2)  NOT NULL,
    currency               CHAR(3)         NOT NULL,
    status                 VARCHAR(20)     NOT NULL,
    idempotency_key        VARCHAR(64)     NOT NULL,
    provider_preference_id VARCHAR(100)    NULL,
    provider_payment_id    VARCHAR(64)     NULL,
    provider_status        VARCHAR(40)     NULL,
    checkout_url           VARCHAR(500)    NULL,
    credited_tx_public_id  CHAR(26)        NULL,
    refunded_mxn           DECIMAL(19, 2)  NOT NULL DEFAULT 0,
    created_at             DATETIME(6)     NOT NULL,
    updated_at             DATETIME(6)     NOT NULL,
    version                INT             NOT NULL DEFAULT 0,
    CONSTRAINT pk_payment_intents PRIMARY KEY (id),
    CONSTRAINT uq_payment_intents_public UNIQUE (public_id),
    CONSTRAINT uq_payment_intents_idem UNIQUE (user_id, idempotency_key),
    CONSTRAINT uq_payment_intents_payment UNIQUE (provider, provider_payment_id),
    CONSTRAINT fk_payment_intents_user FOREIGN KEY (user_id) REFERENCES users (id),
    CONSTRAINT ck_payment_intents_status CHECK (status IN ('CREATED', 'PENDING', 'APPROVED', 'REJECTED', 'CANCELLED',
                                                           'REVIEW', 'REFUNDED', 'PARTIALLY_REFUNDED')),
    CONSTRAINT ck_payment_intents_amount CHECK (amount_mxn > 0 AND refunded_mxn >= 0 AND refunded_mxn <= amount_mxn)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_payment_intents_user ON payment_intents (user_id, created_at);
CREATE INDEX ix_payment_intents_status ON payment_intents (status, created_at);

-- Cada notificación recibida, válida o no, queda registrada (auditoría y diagnóstico).
CREATE TABLE payment_webhook_events (
    id               BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    provider         VARCHAR(24)     NOT NULL,
    request_id       VARCHAR(100)    NULL,
    topic            VARCHAR(40)     NULL,
    action           VARCHAR(60)     NULL,
    data_id          VARCHAR(64)     NULL,
    signature_valid  BOOLEAN         NOT NULL,
    outcome          VARCHAR(40)     NOT NULL,
    detail           VARCHAR(500)    NULL,
    payload          TEXT            NULL,
    received_at      DATETIME(6)     NOT NULL,
    CONSTRAINT pk_payment_webhook_events PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_payment_webhook_events_data ON payment_webhook_events (provider, data_id, received_at);

-- Devoluciones manuales (el administrador devuelve saldo no usado de una recarga).
CREATE TABLE payment_refunds (
    id                  BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    public_id           CHAR(26)        NOT NULL,
    intent_id           BIGINT UNSIGNED NOT NULL,
    amount_mxn          DECIMAL(19, 2)  NOT NULL,
    status              VARCHAR(16)     NOT NULL,
    provider_refund_id  VARCHAR(64)     NULL,
    note                VARCHAR(500)    NOT NULL,
    requested_by        BIGINT UNSIGNED NOT NULL,
    failure             VARCHAR(500)    NULL,
    created_at          DATETIME(6)     NOT NULL,
    updated_at          DATETIME(6)     NOT NULL,
    CONSTRAINT pk_payment_refunds PRIMARY KEY (id),
    CONSTRAINT uq_payment_refunds_public UNIQUE (public_id),
    CONSTRAINT fk_payment_refunds_intent FOREIGN KEY (intent_id) REFERENCES payment_intents (id),
    CONSTRAINT ck_payment_refunds_status CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED')),
    CONSTRAINT ck_payment_refunds_amount CHECK (amount_mxn > 0)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- Resolución de incidencias con motivo y responsable.
ALTER TABLE billing_incidents
    ADD COLUMN resolution_note VARCHAR(500)    NULL,
    ADD COLUMN resolved_by     BIGINT UNSIGNED NULL;

-- 'audit:read' ya existe desde V1 (asignado al administrador).
INSERT INTO permissions (code, description) VALUES
    ('payments:refund', 'Devolver saldo de recargas, conciliar pagos y resolver incidencias de facturación');
INSERT INTO role_permissions (role_id, permission_id)
SELECT r.id, p.id FROM roles r JOIN permissions p ON p.code = 'payments:refund' WHERE r.code = 'ADMIN';
