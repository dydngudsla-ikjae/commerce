CREATE TABLE categories
(
    id        BIGSERIAL PRIMARY KEY,
    name      VARCHAR(255) NOT NULL,
    parent_id BIGINT REFERENCES categories (id)
);

CREATE TABLE products
(
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(255) NOT NULL,
    description TEXT,
    category_id BIGINT       NOT NULL REFERENCES categories (id),
    status      VARCHAR(20)  NOT NULL DEFAULT 'ON_SALE',
    deleted     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMP    NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMP    NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_products_status CHECK (status IN ('ON_SALE', 'STOPPED'))
);

CREATE TABLE product_options
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT       NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    name       VARCHAR(255) NOT NULL,

    CONSTRAINT uq_product_options_product_name UNIQUE (product_id, name)
);

CREATE TABLE product_option_values
(
    id           BIGSERIAL PRIMARY KEY,
    option_id    BIGINT       NOT NULL REFERENCES product_options (id) ON DELETE CASCADE,
    option_value VARCHAR(255) NOT NULL
);

CREATE TABLE product_variants
(
    id         BIGSERIAL PRIMARY KEY,
    product_id BIGINT      NOT NULL REFERENCES products (id) ON DELETE CASCADE,
    price      BIGINT      NOT NULL,
    stock      INT         NOT NULL,
    status     VARCHAR(20) NOT NULL DEFAULT 'ON_SALE',
    created_at TIMESTAMP   NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP   NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_product_variants_status CHECK (status IN ('ON_SALE', 'SOLD_OUT', 'STOPPED'))
);

CREATE TABLE product_variant_options
(
    id              BIGSERIAL PRIMARY KEY,
    variant_id      BIGINT NOT NULL REFERENCES product_variants (id) ON DELETE CASCADE,
    option_value_id BIGINT NOT NULL REFERENCES product_option_values (id) ON DELETE CASCADE,

    CONSTRAINT uq_product_variant_options UNIQUE (variant_id, option_value_id)
);