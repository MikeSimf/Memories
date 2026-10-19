-- Справочник рас: code — машинный ключ, name — как показывать.
-- Раса у героя и персонажа остаётся свободной строкой, но редактор
-- предлагает значения из этого справочника.
CREATE TABLE IF NOT EXISTS races (
    code       VARCHAR(48) PRIMARY KEY,
    name       VARCHAR(96) NOT NULL,
    sort_order INTEGER     NOT NULL DEFAULT 0
);

INSERT INTO races (code, name, sort_order) VALUES
    ('human',      'Человек',            10),
    ('elf',        'Эльф',               20),
    ('half_elf',   'Полуэльф',           30),
    ('dwarf',      'Дварф',              40),
    ('halfling',   'Полурослик',         50),
    ('gnome',      'Гном',               60),
    ('half_orc',   'Полуорк',            70),
    ('tiefling',   'Тифлинг',            80),
    ('dragonborn', 'Драконорождённый',   90),
    ('orc',        'Орк',               100),
    ('goblin',     'Гоблин',            110),
    ('kobold',     'Кобольд',           120),
    ('drow',       'Дроу',              130),
    ('aasimar',    'Аасимар',           140),
    ('genasi',     'Дженази',           150),
    ('goliath',    'Голиаф',            160)
ON CONFLICT (code) DO NOTHING;
