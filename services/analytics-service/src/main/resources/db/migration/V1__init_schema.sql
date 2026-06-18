CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE analytics_events (
    id           UUID         PRIMARY KEY DEFAULT uuid_generate_v4(),
    event_type   VARCHAR(60)  NOT NULL,
    topic        VARCHAR(100) NOT NULL,
    user_id      UUID,
    entity_id    UUID,
    amount       DECIMAL(10,2),
    tier         SMALLINT,
    event_time   TIMESTAMPTZ  NOT NULL,
    received_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    raw_payload  TEXT
);

-- Fast filtering by event type (used by all three aggregation queries)
CREATE INDEX idx_analytics_event_type      ON analytics_events(event_type);

-- Time-range scans for occupancy and revenue queries
CREATE INDEX idx_analytics_event_time      ON analytics_events(event_time DESC);

-- Composite: event type + time range (the most common query pattern)
CREATE INDEX idx_analytics_type_time       ON analytics_events(event_type, event_time DESC);

-- Tier filtering for violation queries
CREATE INDEX idx_analytics_tier            ON analytics_events(tier) WHERE tier IS NOT NULL;
