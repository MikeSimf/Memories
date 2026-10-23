-- Обратная связь: сообщения авторизованных пользователей. Читает только админ.
CREATE TABLE IF NOT EXISTS feedback (
    id         UUID PRIMARY KEY,
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    email      VARCHAR(320) NOT NULL,
    message    TEXT         NOT NULL,
    page       VARCHAR(200),
    status     VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    created_at TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_feedback_created ON feedback (created_at DESC);
