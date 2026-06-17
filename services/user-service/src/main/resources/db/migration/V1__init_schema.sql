CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

CREATE TABLE users (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    name       VARCHAR(100)  NOT NULL,
    email      VARCHAR(150)  NOT NULL UNIQUE,
    password   VARCHAR(255)  NOT NULL,
    phone      VARCHAR(20),
    created_at TIMESTAMPTZ   NOT NULL DEFAULT NOW()
);

CREATE TABLE vehicles (
    id      UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    plate   VARCHAR(20)  NOT NULL UNIQUE,
    make    VARCHAR(50),
    model   VARCHAR(50)
);

CREATE TABLE refresh_tokens (
    id         UUID PRIMARY KEY DEFAULT uuid_generate_v4(),
    user_id    UUID         NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token      VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ  NOT NULL
);

CREATE INDEX idx_users_email          ON users(email);
CREATE INDEX idx_vehicles_user_id     ON vehicles(user_id);
CREATE INDEX idx_refresh_tokens_token ON refresh_tokens(token);
CREATE INDEX idx_refresh_tokens_user  ON refresh_tokens(user_id);
