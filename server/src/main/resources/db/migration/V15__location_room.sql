-- «Помещение»: кафе, рестораны, дома знакомых, поместья.
INSERT INTO location_types (code, name, sort_order) VALUES
    ('room', 'Помещение', 170)
ON CONFLICT (code) DO NOTHING;
