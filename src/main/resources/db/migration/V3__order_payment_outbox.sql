ALTER TABLE orders
    ADD COLUMN paid_at TIMESTAMPTZ,
    ADD COLUMN cancelled_at TIMESTAMPTZ;

ALTER TABLE orders
    ADD CONSTRAINT orders_status_chk CHECK (status IN ('CREATED', 'PAID', 'CANCELLED'));

CREATE INDEX idx_orders_unpaid ON orders (created_at) WHERE status = 'CREATED';

CREATE TABLE payments (
    id          BIGSERIAL PRIMARY KEY,
    order_id    BIGINT NOT NULL REFERENCES orders (id),
    payment_no  VARCHAR(64) NOT NULL UNIQUE,
    amount_cent BIGINT NOT NULL CHECK (amount_cent >= 0),
    status      VARCHAR(32) NOT NULL CHECK (status IN ('SUCCESS')),
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_payments_order ON payments (order_id);

CREATE TABLE outbox_events (
    id              BIGSERIAL PRIMARY KEY,
    event_type      VARCHAR(64) NOT NULL,
    aggregate_id    BIGINT NOT NULL,
    payload         TEXT NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'PROCESSING', 'SENT', 'FAILED')),
    attempts        INT NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    last_error      TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    sent_at         TIMESTAMPTZ
);

CREATE INDEX idx_outbox_due ON outbox_events (next_attempt_at) WHERE status IN ('PENDING', 'PROCESSING');
