-- Типы локаций и предметов хранятся названиями, как раса и класс: клиенты
-- показывают и присылают одно и то же значение, без перевода кодов.
-- V10 записала коды — переводим их в названия справочника.
UPDATE locations l
SET location_type = t.name
FROM location_types t
WHERE l.location_type = t.code;

UPDATE items i
SET item_type = t.name
FROM item_types t
WHERE i.item_type = t.code;

UPDATE locations SET location_type = 'Неизвестно'
WHERE location_type IS NULL OR btrim(location_type) = '';

UPDATE items SET item_type = 'Неизвестно'
WHERE item_type IS NULL OR btrim(item_type) = '';
