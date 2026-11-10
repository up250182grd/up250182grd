-- EnglishOS — V2: bitácora de auditoría (solo INSERT desde la aplicación).

CREATE TABLE audit_logs (
    id             BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    actor_user_id  BIGINT UNSIGNED NULL,
    action         VARCHAR(64)     NOT NULL,
    target_type    VARCHAR(64)     NOT NULL,
    target_id      VARCHAR(64)     NOT NULL,
    before_state   JSON            NULL,
    after_state    JSON            NULL,
    trace_id       VARCHAR(64)     NULL,
    created_at     DATETIME(6)     NOT NULL,
    CONSTRAINT pk_audit_logs PRIMARY KEY (id),
    CONSTRAINT fk_audit_logs_actor FOREIGN KEY (actor_user_id) REFERENCES users (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
CREATE INDEX ix_audit_logs_target ON audit_logs (target_type, target_id);
CREATE INDEX ix_audit_logs_actor_created ON audit_logs (actor_user_id, created_at);
