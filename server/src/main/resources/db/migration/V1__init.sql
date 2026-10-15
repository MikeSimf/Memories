-- DnD Memory: начальная схема
-- Все идентификаторы — uuid, генерируются приложением (в том числе клиентом офлайн).

CREATE EXTENSION IF NOT EXISTS "pgcrypto";

-- 1. Пользователи ------------------------------------------------------------

CREATE TABLE users (
    id               uuid PRIMARY KEY,
    email            varchar(320) NOT NULL UNIQUE,
    password_hash    varchar(120),
    display_name     varchar(120) NOT NULL,
    avatar_file_id   uuid,
    provider         varchar(20)  NOT NULL DEFAULT 'LOCAL',
    provider_user_id varchar(190),
    created_at       timestamptz  NOT NULL DEFAULT now(),
    updated_at       timestamptz  NOT NULL DEFAULT now(),
    CONSTRAINT users_provider_unique UNIQUE (provider, provider_user_id)
);

CREATE TABLE refresh_tokens (
    id          uuid PRIMARY KEY,
    user_id     uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  varchar(64) NOT NULL UNIQUE,
    expires_at  timestamptz NOT NULL,
    revoked     boolean     NOT NULL DEFAULT false,
    user_agent  varchar(255),
    created_at  timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX refresh_tokens_user_idx ON refresh_tokens (user_id);

-- 2. Кампании, участники, приглашения ---------------------------------------

CREATE TABLE campaigns (
    id            uuid PRIMARY KEY,
    title         varchar(200) NOT NULL,
    description   text,
    owner_user_id uuid         NOT NULL REFERENCES users (id),
    revision      bigint       NOT NULL DEFAULT 0,
    deleted       boolean      NOT NULL DEFAULT false,
    created_at    timestamptz  NOT NULL DEFAULT now(),
    updated_at    timestamptz  NOT NULL DEFAULT now()
);
CREATE INDEX campaigns_owner_idx ON campaigns (owner_user_id);

CREATE TABLE members (
    id               uuid PRIMARY KEY,
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    user_id          uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role             varchar(20) NOT NULL,
    linked_entity_id uuid,
    status           varchar(20) NOT NULL DEFAULT 'ACTIVE',
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT members_unique UNIQUE (campaign_id, user_id)
);
CREATE INDEX members_user_idx ON members (user_id);

CREATE TABLE invites (
    id               uuid PRIMARY KEY,
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    token            varchar(16) NOT NULL UNIQUE,
    role             varchar(20) NOT NULL,
    linked_entity_id uuid,
    created_by       uuid        NOT NULL REFERENCES users (id),
    expires_at       timestamptz NOT NULL,
    max_uses         int         NOT NULL DEFAULT 1,
    uses             int         NOT NULL DEFAULT 0,
    revoked          boolean     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX invites_campaign_idx ON invites (campaign_id);

-- 3. Объекты справочника (герои, НПС, локации, предметы) ---------------------

CREATE TABLE entities (
    id            uuid PRIMARY KEY,
    campaign_id   uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    client_id     uuid        NOT NULL,
    type          varchar(20) NOT NULL,
    name          varchar(200) NOT NULL,
    summary       text,
    description   text,
    attrs         jsonb       NOT NULL DEFAULT '{}'::jsonb,
    tags          jsonb       NOT NULL DEFAULT '[]'::jsonb,
    image_file_id uuid,
    created_by    uuid        NOT NULL REFERENCES users (id),
    updated_by    uuid        NOT NULL REFERENCES users (id),
    revision      bigint      NOT NULL DEFAULT 0,
    deleted       boolean     NOT NULL DEFAULT false,
    created_at    timestamptz NOT NULL DEFAULT now(),
    updated_at    timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT entities_client_unique UNIQUE (campaign_id, client_id)
);
CREATE INDEX entities_campaign_type_idx ON entities (campaign_id, type) WHERE NOT deleted;
CREATE INDEX entities_campaign_revision_idx ON entities (campaign_id, revision);
CREATE INDEX entities_name_lower_idx ON entities (campaign_id, lower(name));

-- 4. Пересказы (черновики на сервере не хранятся) ---------------------------

CREATE TABLE retellings (
    id               uuid PRIMARY KEY,
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    client_id        uuid        NOT NULL,
    title            varchar(200) NOT NULL,
    body             text,
    author_entity_id uuid,
    session_date     date,
    status           varchar(20) NOT NULL DEFAULT 'PUBLISHED',
    created_by       uuid        NOT NULL REFERENCES users (id),
    updated_by       uuid        NOT NULL REFERENCES users (id),
    revision         bigint      NOT NULL DEFAULT 0,
    deleted          boolean     NOT NULL DEFAULT false,
    created_at       timestamptz NOT NULL DEFAULT now(),
    updated_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT retellings_client_unique UNIQUE (campaign_id, client_id)
);
CREATE INDEX retellings_campaign_idx ON retellings (campaign_id) WHERE NOT deleted;

-- 5. Журнал изменений для офлайн-синхронизации ------------------------------

CREATE TABLE change_log (
    id               uuid PRIMARY KEY,
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    revision         bigint      NOT NULL,
    object_type      varchar(20) NOT NULL,
    object_id        uuid        NOT NULL,
    op               varchar(10) NOT NULL,
    payload          jsonb       NOT NULL,
    actor_user_id    uuid        NOT NULL,
    client_change_id varchar(64),
    device_id        varchar(64),
    created_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT change_log_revision_unique UNIQUE (campaign_id, revision),
    CONSTRAINT change_log_client_change_unique UNIQUE (campaign_id, client_change_id)
);
CREATE INDEX change_log_campaign_revision_idx ON change_log (campaign_id, revision);

CREATE TABLE sync_conflicts (
    id               uuid PRIMARY KEY,
    campaign_id      uuid        NOT NULL REFERENCES campaigns (id) ON DELETE CASCADE,
    object_type      varchar(20) NOT NULL,
    object_id        uuid        NOT NULL,
    client_change_id varchar(64),
    actor_user_id    uuid        NOT NULL,
    reason           varchar(40) NOT NULL,
    server_payload   jsonb,
    client_payload   jsonb,
    created_at       timestamptz NOT NULL DEFAULT now()
);
CREATE INDEX sync_conflicts_campaign_idx ON sync_conflicts (campaign_id, created_at DESC);

-- 6. Файлы ------------------------------------------------------------------

CREATE TABLE stored_files (
    id           uuid PRIMARY KEY,
    owner_user_id uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_name    varchar(255) NOT NULL,
    content_type varchar(120) NOT NULL,
    size_bytes   bigint      NOT NULL,
    storage_path varchar(500) NOT NULL,
    created_at   timestamptz NOT NULL DEFAULT now()
);
