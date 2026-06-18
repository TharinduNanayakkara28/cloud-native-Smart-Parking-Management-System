CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE notifications (
    id          UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id     UUID         NOT NULL,
    type        VARCHAR(50)  NOT NULL,
    title       VARCHAR(200) NOT NULL,
    message     TEXT         NOT NULL,
    channel     VARCHAR(20)  NOT NULL DEFAULT 'IN_APP',
    read        BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    read_at     TIMESTAMPTZ
);

CREATE INDEX idx_notifications_user_id    ON notifications(user_id);
CREATE INDEX idx_notifications_created_at ON notifications(created_at DESC);
CREATE INDEX idx_notifications_unread     ON notifications(user_id, read) WHERE read = FALSE;
