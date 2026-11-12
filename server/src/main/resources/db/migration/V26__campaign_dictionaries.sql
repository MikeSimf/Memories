-- Отключённые значения справочников на уровне кампании.
CREATE TABLE IF NOT EXISTS campaign_dictionary_disabled (
    campaign_id UUID        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    kind        VARCHAR(24) NOT NULL,
    name        VARCHAR(120) NOT NULL,
    PRIMARY KEY (campaign_id, kind, name)
);
