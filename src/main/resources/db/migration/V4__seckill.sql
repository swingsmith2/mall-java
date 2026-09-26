ALTER TABLE orders
    ADD COLUMN seckill_activity_id BIGINT;

CREATE INDEX idx_orders_seckill ON orders (seckill_activity_id) WHERE seckill_activity_id IS NOT NULL;

CREATE TABLE seckill_activities (
    id                 BIGSERIAL PRIMARY KEY,
    product_id         BIGINT NOT NULL REFERENCES products (id),
    seckill_price_cent BIGINT NOT NULL CHECK (seckill_price_cent >= 0),
    stock              INT NOT NULL CHECK (stock > 0),
    start_at           TIMESTAMPTZ NOT NULL,
    end_at             TIMESTAMPTZ NOT NULL,
    per_user_limit     INT NOT NULL DEFAULT 1 CHECK (per_user_limit > 0),
    status             VARCHAR(16) NOT NULL DEFAULT 'RUNNING' CHECK (status IN ('RUNNING', 'ENDED')),
    created_at         TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CHECK (end_at > start_at)
);

CREATE INDEX idx_seckill_running ON seckill_activities (end_at) WHERE status = 'RUNNING';
