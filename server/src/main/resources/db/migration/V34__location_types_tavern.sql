INSERT INTO location_types (code, name, sort_order) VALUES
    ('tavern', 'Таверна', 30),
    ('shop', 'Магазин', 40),
    ('event', 'Событие', 50)
ON CONFLICT (code) DO NOTHING;
