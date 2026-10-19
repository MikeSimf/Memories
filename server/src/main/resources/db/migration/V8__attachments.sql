-- Файлы объекта: массив {id, name, contentType, size} в jsonb.
-- Одно поле вместо отдельной таблицы: список едет внутри payload объекта,
-- поэтому синхронизация и офлайн-правки работают без нового типа изменений.
ALTER TABLE heroes     ADD COLUMN IF NOT EXISTS files JSONB;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS files JSONB;
ALTER TABLE locations  ADD COLUMN IF NOT EXISTS files JSONB;
ALTER TABLE items      ADD COLUMN IF NOT EXISTS files JSONB;
ALTER TABLE materials  ADD COLUMN IF NOT EXISTS files JSONB;
