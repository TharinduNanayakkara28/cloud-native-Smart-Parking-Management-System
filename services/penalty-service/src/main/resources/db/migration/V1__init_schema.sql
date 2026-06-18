CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE penalties (
    id              UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    reservation_id  UUID         NOT NULL,
    user_id         UUID         NOT NULL,
    spot_id         UUID         NOT NULL,
    type            VARCHAR(20)  NOT NULL,
    tier            SMALLINT     NOT NULL,
    amount          DECIMAL(10,2) NOT NULL,
    status          VARCHAR(20)  NOT NULL DEFAULT 'ISSUED',
    issued_at       TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    paid_at         TIMESTAMPTZ
);

CREATE INDEX idx_penalties_user_id        ON penalties(user_id);
CREATE INDEX idx_penalties_reservation_id ON penalties(reservation_id);
CREATE INDEX idx_penalties_status         ON penalties(status);
-- Prevents issuing the same tier twice for the same reservation
CREATE UNIQUE INDEX idx_penalties_reservation_tier ON penalties(reservation_id, tier);
