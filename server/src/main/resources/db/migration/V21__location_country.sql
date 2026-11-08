-- «Страна»: крупная географическая единица для карты кампании.
INSERT INTO location_types (code, name, sort_order) VALUES
    ('country', 'Страна', 180)
ON CONFLICT (code) DO NOTHING;
