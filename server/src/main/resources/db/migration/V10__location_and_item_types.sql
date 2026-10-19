-- Типы локаций и предметов: по ним выбирается картинка-аватар по умолчанию.
CREATE TABLE IF NOT EXISTS location_types (
    code       VARCHAR(48) PRIMARY KEY,
    name       VARCHAR(96) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

INSERT INTO location_types (code, name, sort_order) VALUES
    ('unknown',     'Неизвестно',        0),
    ('city',        'Город',            10),
    ('village',     'Деревня',          20),
    ('forest',      'Лес',              30),
    ('plain',       'Равнина',          40),
    ('steppe',      'Степь',            50),
    ('crossroads',  'Перекрёсток',      60),
    ('otherworld',  'Другое измерение', 70),
    ('mountains',   'Горы',             80),
    ('cave',        'Пещера',           90),
    ('dungeon',     'Подземелье',      100),
    ('ruins',       'Руины',           110),
    ('tower',       'Башня',           120),
    ('swamp',       'Болото',          130),
    ('coast',       'Побережье',       140),
    ('sea',         'Море',            150),
    ('desert',      'Пустыня',         160)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS item_types (
    code       VARCHAR(48) PRIMARY KEY,
    name       VARCHAR(96) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

INSERT INTO item_types (code, name, sort_order) VALUES
    ('unknown',   'Неизвестно',  0),
    ('weapon',    'Оружие',     10),
    ('armor',     'Броня',      20),
    ('artifact',  'Артефакт',   30),
    ('jewelry',   'Драгоценность', 40),
    ('potion',    'Зелье',      50),
    ('scroll',    'Свиток',     60),
    ('trophy',    'Трофей',     70),
    ('tool',      'Инструмент', 80),
    ('book',      'Книга',      90),
    ('transport', 'Транспорт', 100),
    ('treasure',  'Сокровище', 110),
    ('other',     'Другое',    120)
ON CONFLICT (code) DO NOTHING;

ALTER TABLE locations ADD COLUMN IF NOT EXISTS location_type VARCHAR(48);
ALTER TABLE items     ADD COLUMN IF NOT EXISTS item_type     VARCHAR(48);

UPDATE locations SET location_type = 'unknown' WHERE location_type IS NULL;
UPDATE items     SET item_type = 'unknown'     WHERE item_type IS NULL;
