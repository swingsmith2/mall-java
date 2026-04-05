CREATE TABLE users (
    id          BIGSERIAL PRIMARY KEY,
    username    VARCHAR(64) NOT NULL UNIQUE,
    password    VARCHAR(255) NOT NULL,
    role        VARCHAR(32) NOT NULL DEFAULT 'USER',
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE categories (
    id          BIGSERIAL PRIMARY KEY,
    name        VARCHAR(128) NOT NULL,
    parent_id   BIGINT REFERENCES categories (id),
    sort_order  INT NOT NULL DEFAULT 0
);

CREATE TABLE products (
    id           BIGSERIAL PRIMARY KEY,
    category_id  BIGINT NOT NULL REFERENCES categories (id),
    name         VARCHAR(255) NOT NULL,
    description  TEXT,
    price_cent   BIGINT NOT NULL CHECK (price_cent >= 0),
    stock        INT NOT NULL CHECK (stock >= 0),
    status       SMALLINT NOT NULL DEFAULT 1,
    version      INT NOT NULL DEFAULT 0,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_products_category ON products (category_id);
CREATE INDEX idx_products_status ON products (status);

CREATE TABLE orders (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT NOT NULL REFERENCES users (id),
    total_cent   BIGINT NOT NULL CHECK (total_cent >= 0),
    status       VARCHAR(32) NOT NULL DEFAULT 'CREATED',
    idempotent_key VARCHAR(128),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (user_id, idempotent_key)
);

CREATE INDEX idx_orders_user ON orders (user_id);

CREATE TABLE order_items (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders (id) ON DELETE CASCADE,
    product_id  BIGINT NOT NULL REFERENCES products (id),
    qty         INT NOT NULL CHECK (qty > 0),
    price_cent  BIGINT NOT NULL CHECK (price_cent >= 0)
);

CREATE INDEX idx_order_items_order ON order_items (order_id);

-- 演示数据
INSERT INTO categories (id, name, parent_id, sort_order) VALUES
    (1, '数码', NULL, 1),
    (2, '家居', NULL, 2);

INSERT INTO products (category_id, name, description, price_cent, stock, status) VALUES
    (1, '无线耳机', '降噪', 59900, 5000, 1),
    (1, '机械键盘', '热插拔', 89900, 2000, 1),
    (2, '保温杯', '316 不锈钢', 12900, 10000, 1);

SELECT setval(pg_get_serial_sequence('categories', 'id'), COALESCE((SELECT MAX(id) FROM categories), 1));
