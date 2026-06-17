CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE reservations (
    id              UUID        PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id         UUID        NOT NULL,
    spot_id         UUID        NOT NULL,
    vehicle_plate   VARCHAR(20),
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    reserved_from   TIMESTAMPTZ,
    reserved_until  TIMESTAMPTZ,
    checked_in_at   TIMESTAMPTZ,
    checked_out_at  TIMESTAMPTZ,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reservations_user_id       ON reservations(user_id);
CREATE INDEX idx_reservations_spot_id       ON reservations(spot_id);
CREATE INDEX idx_reservations_status        ON reservations(status);
CREATE INDEX idx_reservations_reserved_until ON reservations(reserved_until);
