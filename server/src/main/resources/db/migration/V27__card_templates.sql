-- Шаблоны карточек списков: условие, приоритет и блоки.
CREATE TABLE IF NOT EXISTS card_templates (
    id          UUID PRIMARY KEY,
    name        VARCHAR(120) NOT NULL,
    object_type VARCHAR(24)  NOT NULL,
    kind        VARCHAR(16)  NOT NULL DEFAULT 'LIST',
    formula     TEXT         NOT NULL DEFAULT 'true',
    priority    INTEGER      NOT NULL DEFAULT 0,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS card_blocks (
    id          UUID PRIMARY KEY,
    template_id UUID NOT NULL REFERENCES card_templates (id) ON DELETE CASCADE,
    field       VARCHAR(48) NOT NULL,
    display     VARCHAR(16) NOT NULL DEFAULT 'TEXT',
    x           REAL NOT NULL DEFAULT 0,
    y           REAL NOT NULL DEFAULT 0,
    width       REAL NOT NULL DEFAULT 100,
    height      REAL NOT NULL DEFAULT 24,
    font_size   REAL NOT NULL DEFAULT 14,
    sort_order  INTEGER NOT NULL DEFAULT 0
);
