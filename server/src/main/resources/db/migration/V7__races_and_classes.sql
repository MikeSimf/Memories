-- Расы и классы становятся закрытыми справочниками: значения вне списка
-- не допускаются, у обоих есть «Неизвестно» (по умолчанию) и «Без …».

INSERT INTO races (code, name, sort_order) VALUES
    ('unknown', 'Неизвестно',    0),
    ('none',    'Без расы',   1000)
ON CONFLICT (code) DO NOTHING;

CREATE TABLE IF NOT EXISTS character_classes (
    code       VARCHAR(48) PRIMARY KEY,
    name       VARCHAR(96) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

INSERT INTO character_classes (code, name, sort_order) VALUES
    ('unknown',   'Неизвестно',      0),
    ('barbarian', 'Варвар',         10),
    ('bard',      'Бард',           20),
    ('cleric',    'Жрец',           30),
    ('druid',     'Друид',          40),
    ('fighter',   'Воин',           50),
    ('monk',      'Монах',          60),
    ('paladin',   'Паладин',        70),
    ('ranger',    'Следопыт',       80),
    ('rogue',     'Плут',           90),
    ('sorcerer',  'Чародей',       100),
    ('warlock',   'Колдун',        110),
    ('wizard',    'Волшебник',     120),
    ('artificer', 'Изобретатель',  130),
    ('none',      'Без класса',   1000)
ON CONFLICT (code) DO NOTHING;

-- Старые свободные значения приводим к справочнику: всё, чего в нём нет,
-- становится «Неизвестно».
UPDATE heroes SET race = 'Неизвестно'
WHERE race IS NULL OR btrim(race) = '' OR race NOT IN (SELECT name FROM races);

UPDATE characters SET race = 'Неизвестно'
WHERE race IS NULL OR btrim(race) = '' OR race NOT IN (SELECT name FROM races);

UPDATE heroes SET klass = 'Неизвестно'
WHERE klass IS NULL OR btrim(klass) = '' OR klass NOT IN (SELECT name FROM character_classes);
