-- Почта больше не обязательна: связь идёт через форму обратной связи и VK.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
