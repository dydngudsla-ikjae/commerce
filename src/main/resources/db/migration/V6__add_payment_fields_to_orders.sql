-- orders 테이블에 결제 관련 컬럼 추가
ALTER TABLE orders
    ADD COLUMN pg_transaction_id VARCHAR(255),
    ADD COLUMN retry_count       INT NOT NULL DEFAULT 0,
    ADD COLUMN last_retry_at     TIMESTAMP;

-- status CHECK constraint 수정: 기존 constraint 삭제 후 새 상태 포함
ALTER TABLE orders DROP CONSTRAINT IF EXISTS chk_orders_status;

ALTER TABLE orders
    ADD CONSTRAINT chk_orders_status
        CHECK (status IN ('PENDING', 'PAYMENT_IN_PROGRESS', 'PAID', 'CANCELLED', 'PAYMENT_FAILED'));