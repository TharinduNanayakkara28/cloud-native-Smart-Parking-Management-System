CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE payments (
    id               UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id   UUID         NOT NULL,
    user_id          UUID         NOT NULL,
    amount           DECIMAL(10,2) NOT NULL,
    currency         VARCHAR(5)   NOT NULL DEFAULT 'USD',
    status           VARCHAR(20)  NOT NULL DEFAULT 'PENDING',
    provider_ref     VARCHAR(100),
    idempotency_key  VARCHAR(200) UNIQUE,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX idx_payments_reservation_id ON payments(reservation_id);
CREATE INDEX idx_payments_user_id          ON payments(user_id);
CREATE INDEX idx_payments_status           ON payments(status);
