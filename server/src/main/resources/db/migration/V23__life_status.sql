-- Статус жизни героя и персонажа: alive / dead / unknown.
ALTER TABLE heroes ADD COLUMN IF NOT EXISTS life_status VARCHAR(16) NOT NULL DEFAULT 'unknown';
ALTER TABLE characters ADD COLUMN IF NOT EXISTS life_status VARCHAR(16) NOT NULL DEFAULT 'unknown';
