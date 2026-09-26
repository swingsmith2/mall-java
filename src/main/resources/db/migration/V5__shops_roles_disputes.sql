UPDATE users SET role = 'SUPER_ADMIN' WHERE role = 'ADMIN';

INSERT INTO users (username, password, role)
SELECT 'cs', '$2b$12$Ngx1DeyBBJtJP3SsxmuZV.aU01IogzCZsHQW7k90ildgie.8pIXSy', 'CS'
WHERE NOT EXISTS (SELECT 1 FROM users WHERE username = 'cs');

CREATE TABLE shops (
    id                   BIGSERIAL PRIMARY KEY,
    owner_id             BIGINT NOT NULL UNIQUE REFERENCES users (id),
    name                 VARCHAR(128) NOT NULL,
    description          TEXT,
    status               VARCHAR(32) NOT NULL DEFAULT 'OPEN'
        CHECK (status IN ('OPEN', 'FORCED_CLOSED')),
    forced_close_reason  TEXT,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

ALTER TABLE products
    ADD COLUMN shop_id BIGINT REFERENCES shops (id);

CREATE INDEX idx_products_shop ON products (shop_id);

ALTER TABLE orders DROP CONSTRAINT orders_status_chk;
ALTER TABLE orders ADD CONSTRAINT orders_status_chk
    CHECK (status IN ('CREATED', 'PAID', 'CANCELLED', 'REFUNDED'));

CREATE TABLE disputes (
    id               BIGSERIAL PRIMARY KEY,
    order_id         BIGINT NOT NULL REFERENCES orders (id),
    opener_id        BIGINT NOT NULL REFERENCES users (id),
    reason           TEXT NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'RESOLVED')),
    resolution       VARCHAR(16),
    resolution_note  TEXT,
    handler_id       BIGINT REFERENCES users (id),
    created_at       TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    resolved_at      TIMESTAMPTZ
);

CREATE UNIQUE INDEX uq_disputes_open_order ON disputes (order_id) WHERE status = 'OPEN';
CREATE INDEX idx_disputes_status ON disputes (status, id);
