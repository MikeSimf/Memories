CREATE TABLE IF NOT EXISTS quests (
    id UUID PRIMARY KEY,
    campaign_id UUID NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name VARCHAR(200) NOT NULL,
    description TEXT,
    avatar_file_id UUID REFERENCES stored_files (id) ON DELETE SET NULL,
    created_by UUID,
    updated_by UUID,
    deleted BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_quests_campaign ON quests (campaign_id) WHERE deleted = FALSE;

CREATE TABLE IF NOT EXISTS quest_comments (
    id UUID PRIMARY KEY,
    quest_id UUID NOT NULL REFERENCES quests (id) ON DELETE CASCADE,
    text TEXT NOT NULL,
    created_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    deleted BOOLEAN NOT NULL DEFAULT FALSE
);
CREATE INDEX IF NOT EXISTS idx_quest_comments_quest ON quest_comments (quest_id) WHERE deleted = FALSE;
