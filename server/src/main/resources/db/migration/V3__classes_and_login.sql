-- Переработка v2: вход по логину и паролю, четыре класса объектов вместо entities.
--
-- Развёрнутых данных нет, поэтому схема приводится к чистому виду: устройства и
-- внешние провайдеры входа удаляются, объекты справочника переезжают из одной
-- таблицы entities в четыре свои (docs/redesign-v2.md, раздел 4).

-- 1. Пользователи: логин и подтверждение почты ------------------------------

ALTER TABLE users ADD COLUMN login varchar(32);

-- Почта снова обязательна: пароль восстанавливается только на неё.
UPDATE users
   SET login = 'user_' || substr(replace(id::text, '-', ''), 1, 20)
 WHERE login IS NULL;
UPDATE users
   SET email = 'user_' || id::text || '@dnd-memory.local'
 WHERE email IS NULL;

ALTER TABLE users ALTER COLUMN login SET NOT NULL;
ALTER TABLE users ALTER COLUMN email SET NOT NULL;
ALTER TABLE users ADD COLUMN email_verified boolean NOT NULL DEFAULT false;
ALTER TABLE users ADD CONSTRAINT users_login_unique UNIQUE (login);

-- Вход через Яндекс и VK удалён полностью.
ALTER TABLE users DROP COLUMN provider;
ALTER TABLE users DROP COLUMN provider_user_id;

-- 2. Приглашения: строго одноразовые ----------------------------------------

ALTER TABLE invites DROP COLUMN mode;
ALTER TABLE invites DROP COLUMN display_name;
ALTER TABLE invites DROP COLUMN label;
UPDATE invites SET max_uses = 1 WHERE max_uses <> 1;
ALTER TABLE invites ALTER COLUMN max_uses SET DEFAULT 1;
ALTER TABLE invites RENAME COLUMN linked_entity_id TO linked_hero_id;

-- 3. Устройства и старая таблица объектов -----------------------------------

DROP TABLE devices;
DROP TABLE entities;

ALTER TABLE refresh_tokens DROP COLUMN device_id;
ALTER TABLE refresh_tokens ADD COLUMN platform varchar(20);

-- 4. Четыре класса объектов -------------------------------------------------

CREATE TABLE heroes (
    id             uuid PRIMARY KEY,
    campaign_id    uuid         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name           varchar(200) NOT NULL,
    description    text,
    image_file_id  uuid,
    deleted        boolean      NOT NULL DEFAULT false,
    created_at     timestamptz  NOT NULL DEFAULT now(),
    updated_at     timestamptz  NOT NULL DEFAULT now(),
    created_by     uuid         NOT NULL REFERENCES users (id),
    updated_by     uuid         NOT NULL REFERENCES users (id),
    revision       bigint       NOT NULL DEFAULT 0,
    race           varchar(120),
    klass          varchar(120),
    player_user_id uuid REFERENCES users (id)
);
CREATE INDEX heroes_campaign_idx ON heroes (campaign_id, lower(name)) WHERE NOT deleted;

CREATE TABLE characters (
    id                uuid PRIMARY KEY,
    campaign_id       uuid         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name              varchar(200) NOT NULL,
    description       text,
    image_file_id     uuid,
    deleted           boolean      NOT NULL DEFAULT false,
    created_at        timestamptz  NOT NULL DEFAULT now(),
    updated_at        timestamptz  NOT NULL DEFAULT now(),
    created_by        uuid         NOT NULL REFERENCES users (id),
    updated_by        uuid         NOT NULL REFERENCES users (id),
    revision          bigint       NOT NULL DEFAULT 0,
    race              varchar(120),
    short_description text
);
CREATE INDEX characters_campaign_idx ON characters (campaign_id, lower(name)) WHERE NOT deleted;

CREATE TABLE locations (
    id            uuid PRIMARY KEY,
    campaign_id   uuid         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name          varchar(200) NOT NULL,
    description   text,
    image_file_id uuid,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now(),
    created_by    uuid         NOT NULL REFERENCES users (id),
    updated_by    uuid         NOT NULL REFERENCES users (id),
    revision      bigint       NOT NULL DEFAULT 0
);
CREATE INDEX locations_campaign_idx ON locations (campaign_id, lower(name)) WHERE NOT deleted;

CREATE TABLE items (
    id                 uuid PRIMARY KEY,
    campaign_id        uuid         NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    name               varchar(200) NOT NULL,
    description        text,
    image_file_id      uuid,
    deleted            boolean      NOT NULL DEFAULT false,
    created_at         timestamptz  NOT NULL DEFAULT now(),
    updated_at         timestamptz  NOT NULL DEFAULT now(),
    created_by         uuid         NOT NULL REFERENCES users (id),
    updated_by         uuid         NOT NULL REFERENCES users (id),
    revision           bigint       NOT NULL DEFAULT 0,
    owner_name         varchar(200),
    owner_hero_id      uuid,
    owner_character_id uuid
);
CREATE INDEX items_campaign_idx ON items (campaign_id, lower(name)) WHERE NOT deleted;

-- 5. Предметы в котомке героя -----------------------------------------------

CREATE TABLE hero_items (
    id       uuid PRIMARY KEY,
    hero_id  uuid         NOT NULL REFERENCES heroes (id) ON DELETE CASCADE,
    name     varchar(200) NOT NULL,
    item_id  uuid,
    quantity int,
    notes    text,
    position int          NOT NULL DEFAULT 0
);
CREATE INDEX hero_items_hero_idx ON hero_items (hero_id, position);

-- 6. Сброс пароля без SMTP --------------------------------------------------

CREATE TABLE password_reset_requests (
    id         uuid PRIMARY KEY,
    user_id    uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamptz NOT NULL,
    used       boolean     NOT NULL DEFAULT false,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX password_reset_requests_user_idx ON password_reset_requests (user_id);

-- 7. Привязка участника к герою ---------------------------------------------

ALTER TABLE members RENAME COLUMN linked_entity_id TO linked_hero_id;
