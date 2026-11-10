-- «Объект» и «Семейство»: уточняют географию кампании.
INSERT INTO location_types (code, name, sort_order) VALUES
    ('object', 'Объект', 190),
    ('family', 'Семейство', 200)
ON CONFLICT (code) DO NOTHING;
