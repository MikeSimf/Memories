-- По умолчанию все живы; автор этого массового изменения — system.
UPDATE heroes
SET life_status = 'alive',
    updated_by = COALESCE((SELECT id FROM users WHERE login = 'system'), updated_by),
    updated_at = NOW()
WHERE life_status IS NULL OR life_status = 'unknown';

UPDATE characters
SET life_status = 'alive',
    updated_by = COALESCE((SELECT id FROM users WHERE login = 'system'), updated_by),
    updated_at = NOW()
WHERE life_status IS NULL OR life_status = 'unknown';
