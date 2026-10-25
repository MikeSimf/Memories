-- Отчёты о падениях мобильного приложения: присылаются с устройства.
CREATE TABLE IF NOT EXISTS crash_reports (
    id          UUID PRIMARY KEY,
    user_id     UUID REFERENCES users (id) ON DELETE SET NULL,
    app_version VARCHAR(40),
    device      VARCHAR(200),
    stacktrace  TEXT NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crash_reports_created ON crash_reports (created_at DESC);
