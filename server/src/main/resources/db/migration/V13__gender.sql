-- Пол героя и персонажа: ж, м или «не определён». Хранится кодом,
-- чтобы подписи и иконки можно было менять без миграции данных.
ALTER TABLE heroes     ADD COLUMN IF NOT EXISTS gender VARCHAR(16) NOT NULL DEFAULT 'unknown';
ALTER TABLE characters ADD COLUMN IF NOT EXISTS gender VARCHAR(16) NOT NULL DEFAULT 'unknown';

UPDATE heroes SET gender = 'unknown'
WHERE gender IS NULL OR btrim(gender) = '' OR gender NOT IN ('female', 'male', 'unknown');

UPDATE characters SET gender = 'unknown'
WHERE gender IS NULL OR btrim(gender) = '' OR gender NOT IN ('female', 'male', 'unknown');
