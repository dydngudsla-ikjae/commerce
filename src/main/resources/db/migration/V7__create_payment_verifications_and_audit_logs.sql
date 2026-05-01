-- 결제 검증 이력
CREATE TABLE payment_verifications
(
    id                BIGSERIAL PRIMARY KEY,
    order_id          BIGINT      NOT NULL REFERENCES orders (id),
    pg_transaction_id VARCHAR(255),
    pg_status         VARCHAR(20) NOT NULL,
    verified_by       VARCHAR(255),
    verified_at       TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_payment_verifications_pg_status CHECK (pg_status IN ('SUCCESS', 'FAIL', 'UNKNOWN'))
);

CREATE INDEX idx_payment_verifications_order_id ON payment_verifications (order_id);

-- 관리자 감사 로그
CREATE TABLE audit_logs
(
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT       NOT NULL REFERENCES orders (id),
    action           VARCHAR(100) NOT NULL,
    performed_by     VARCHAR(255),
    reason           TEXT,
    idempotency_key  VARCHAR(255) UNIQUE,
    created_at       TIMESTAMP    NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_audit_logs_order_id ON audit_logs (order_id);