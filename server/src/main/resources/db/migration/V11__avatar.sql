-- Аватар объекта — отдельно от фотографий: фото живут в files, аватар один.
ALTER TABLE heroes     ADD COLUMN IF NOT EXISTS avatar_file_id UUID;
ALTER TABLE characters ADD COLUMN IF NOT EXISTS avatar_file_id UUID;
ALTER TABLE locations  ADD COLUMN IF NOT EXISTS avatar_file_id UUID;
ALTER TABLE items      ADD COLUMN IF NOT EXISTS avatar_file_id UUID;
ALTER TABLE materials  ADD COLUMN IF NOT EXISTS avatar_file_id UUID;
