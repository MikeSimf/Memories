ALTER TABLE races ADD COLUMN IF NOT EXISTS icon_file_id UUID;
ALTER TABLE character_classes ADD COLUMN IF NOT EXISTS icon_file_id UUID;
ALTER TABLE item_types ADD COLUMN IF NOT EXISTS icon_file_id UUID;
ALTER TABLE location_types ADD COLUMN IF NOT EXISTS icon_file_id UUID;
