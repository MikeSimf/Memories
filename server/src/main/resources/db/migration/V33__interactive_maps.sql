CREATE TABLE IF NOT EXISTS interactive_maps (
    id            UUID PRIMARY KEY,
    campaign_id   UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name          VARCHAR(160) NOT NULL,
    description   TEXT,
    image_file_id UUID REFERENCES files (id) ON DELETE SET NULL,
    created_by    UUID,
    updated_by    UUID,
    revision      BIGINT NOT NULL DEFAULT 1,
    deleted       BOOLEAN NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_maps_campaign ON interactive_maps (campaign_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS map_markers (
    id         UUID PRIMARY KEY,
    map_id     UUID NOT NULL REFERENCES interactive_maps (id) ON DELETE CASCADE,
    entity_id  UUID NOT NULL,
    x          DOUBLE PRECISION NOT NULL,
    y          DOUBLE PRECISION NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE (map_id, entity_id)
);
