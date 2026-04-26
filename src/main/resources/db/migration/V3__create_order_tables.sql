CREATE TABLE orders
(
    id           BIGSERIAL PRIMARY KEY,
    member_id    BIGINT      NOT NULL,
    status       VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    total_amount BIGINT      NOT NULL,
    created_at   TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_orders_status CHECK (status IN ('PENDING', 'PAID', 'CANCELLED'))
);

CREATE TABLE order_items
(
    id           BIGSERIAL PRIMARY KEY,
    order_id     BIGINT       NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    variant_id   BIGINT       NOT NULL,
    product_name VARCHAR(255) NOT NULL,
    price        BIGINT       NOT NULL,
    quantity     INT          NOT NULL
);