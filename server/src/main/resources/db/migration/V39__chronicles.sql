CREATE TABLE IF NOT EXISTS chronicles (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    session_date DATE,
    created_by UUID,
    updated_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_chronicles_campaign ON chronicles (campaign_id) WHERE deleted = FALSE;

ALTER TABLE retellings ADD COLUMN IF NOT EXISTS chronicle_id UUID REFERENCES chronicles (id) ON DELETE SET NULL;

CREATE TABLE IF NOT EXISTS chronicle_points (
    id UUID PRIMARY KEY,
    chronicle_id UUID NOT NULL REFERENCES chronicles (id) ON DELETE CASCADE,
    map_id UUID NOT NULL REFERENCES interactive_maps (id) ON DELETE CASCADE,
    hero_id UUID REFERENCES heroes (id) ON DELETE CASCADE,
    whole_team BOOLEAN NOT NULL DEFAULT TRUE,
    x DOUBLE PRECISION NOT NULL,
    y DOUBLE PRECISION NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    teleport BOOLEAN NOT NULL DEFAULT FALSE,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_chronicle_points_chronicle ON chronicle_points (chronicle_id, sort_order);
CREATE INDEX IF NOT EXISTS idx_chronicle_points_map ON chronicle_points (map_id);

ALTER TABLE heroes ADD COLUMN IF NOT EXISTS color VARCHAR(16);
ALTER TABLE campaigns ADD COLUMN IF NOT EXISTS team_color VARCHAR(16);
