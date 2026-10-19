-- Класс у персонажа (НПС): у героя он был с самого начала, теперь и у НПС.
-- Значение — из справочника character_classes (как у героя).
ALTER TABLE characters ADD COLUMN IF NOT EXISTS klass VARCHAR(96);

UPDATE characters SET klass = 'Неизвестно' WHERE klass IS NULL OR btrim(klass) = '';
