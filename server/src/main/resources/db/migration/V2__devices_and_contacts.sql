-- Вход по QR-коду устройства, контакты в профиле и режимы приглашений.
--
-- Идея: админ выпускает QR устройства, телефон сканирует его и получает
-- долгоживущую сессию — без пароля и без SMS. Позже в профиле можно указать
-- почту и телефон и задать пароль, чтобы входить в веб-версию.

-- 1. Контакты пользователя ---------------------------------------------------

ALTER TABLE users ADD COLUMN phone varchar(32);

-- Аккаунт, созданный по QR устройства, может не иметь ни почты, ни пароля:
-- контакты человек добавит позже в профиле.
ALTER TABLE users ALTER COLUMN email DROP NOT NULL;
CREATE UNIQUE INDEX users_phone_unique_idx ON users (phone) WHERE phone IS NOT NULL;

-- 2. Режим приглашения: обычное (участник) или для устройства ---------------

ALTER TABLE invites ADD COLUMN mode varchar(20) NOT NULL DEFAULT 'MEMBER';
-- Имя, которое получит создаваемый пользователь (для режима DEVICE)
ALTER TABLE invites ADD COLUMN display_name varchar(120);
-- Подпись устройства, чтобы админ понимал, чей это телефон
ALTER TABLE invites ADD COLUMN label varchar(120);

-- 3. Устройства --------------------------------------------------------------

CREATE TABLE devices (
    id           uuid PRIMARY KEY,
    user_id      uuid        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    campaign_id  uuid        REFERENCES campaigns (id) ON DELETE SET NULL,
    label        varchar(120),
    platform     varchar(40),
    invite_id    uuid,
    enrolled_by  uuid REFERENCES users (id),
    revoked      boolean     NOT NULL DEFAULT false,
    created_at   timestamptz NOT NULL DEFAULT now(),
    last_seen_at timestamptz
);
CREATE INDEX devices_user_idx ON devices (user_id);
CREATE INDEX devices_campaign_idx ON devices (campaign_id);

-- Привязка refresh-токена к устройству: отзыв устройства гасит его сессии
ALTER TABLE refresh_tokens ADD COLUMN device_id uuid;
CREATE INDEX refresh_tokens_device_idx ON refresh_tokens (device_id);
