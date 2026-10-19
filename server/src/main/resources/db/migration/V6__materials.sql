-- Фото-материалы кампании: фото + название и описание.
-- Таблица повторяет общие поля объектов справочника, поэтому синхронизация
-- и права работают тем же кодом.
CREATE TABLE IF NOT EXISTS materials (
    id            UUID PRIMARY KEY,
    campaign_id   UUID         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name          VARCHAR(200) NOT NULL DEFAULT '',
    description   TEXT,
    image_file_id UUID,
    deleted       BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL,
    created_by    UUID         NOT NULL,
    updated_by    UUID         NOT NULL,
    revision      BIGINT       NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS materials_campaign_idx
    ON materials (campaign_id, deleted);
