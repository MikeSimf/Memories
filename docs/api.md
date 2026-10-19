# DnD Memory — HTTP API (v2)

Контракт между сервером, веб-версией и Android-приложением. Любое изменение здесь
означает синхронную правку всех трёх частей. Решения, из которых выросла вторая
версия, — в [`redesign-v2.md`](redesign-v2.md).

## 1. Общие правила

* Базовый путь: `/api`. Формат — JSON UTF-8 (`Content-Type: application/json`).
* Время — ISO-8601 UTC с миллисекундами: `2025-10-15T11:00:00.000Z`.
* Идентификаторы — UUID в виде строки.
* Аутентификация — `Authorization: Bearer <accessToken>` (JWT, 15 минут).
  Обновление — refresh-токен, хранится на сервере хешем, ротация при каждом обновлении:
  30 дней для веб-версии, 365 — для приложения (см. `platform`).
* Ошибка:

```json
{ "error": { "code": "forbidden", "message": "Недостаточно прав", "details": {} } }
```

Коды: `unauthorized`, `forbidden`, `not_found`, `validation_failed`,
`conflict`, `draft_not_synced`, `invite_invalid`, `email_taken`, `login_taken`,
`phone_taken`, `reset_invalid`, `rate_limited`, `resync_required`, `internal_error`.

* Пагинация — `limit` (по умолчанию 50, максимум 500) и `offset` (кроме синхронизации,
  где используется `since`, и snapshot — там `cursor`).
* Все изменяющие запросы к объектам кампании увеличивают `revision` кампании на 1
  и пишут запись в журнал изменений (см. `sync.md`).

## 2. Перечисления

| Тип | Значения |
|---|---|
| `MemberRole` | `OWNER`, `EDITOR_MASTER`, `EDITOR_HERO`, `VIEWER` |
| `MemberStatus` | `INVITED`, `ACTIVE`, `REMOVED` |
| `RetellingStatus` | `DRAFT` (только локально), `PUBLISHED` |
| `ChangeOp` | `UPSERT`, `DELETE` |
| `ObjectType` | `HERO`, `CHARACTER`, `LOCATION`, `ITEM`, `RETELLING`, `CAMPAIGN`, `MEMBER` |

Права (`myPermissions`) — плоский список строк, вычисляется сервером для текущего
пользователя в кампании:

`campaign.manage`, `members.manage`, `invite.create`, `entity.read`,
`entity.write`, `hero.write`, `retelling.read`, `retelling.write`,
`retelling.write.own`, `retelling.publish`.

Матрица ролей — в `roles.md`. Коротко: героев ведёт только `OWNER`; персонажей,
локации и предметы — `OWNER`, `EDITOR_MASTER` и `EDITOR_HERO`; читают все, включая
`VIEWER`.

## 3. Аутентификация

Вход — только по логину и паролю. Внешних провайдеров (Яндекс, VK) и регистрации
устройства по QR больше нет.

### POST /api/auth/register
```json
{ "login": "gm", "email": "gm@example.com", "password": "secret123",
  "displayName": "Мастер", "inviteToken": "7F3K9Q2M", "platform": "android" }
```
→ `201`
```json
{ "user": { "id": "…", "login": "gm", "email": "gm@example.com", "emailVerified": false,
            "displayName": "Мастер", "phone": null, "avatarUrl": null, "hasPassword": true,
            "createdAt": "…" },
  "accessToken": "…", "refreshToken": "…", "expiresIn": 900 }
```

* `login` — 3–32 символа из `a–z`, `0–9`, `_`, `.`, `-`. Хранится в нижнем регистре;
  регистр при входе не важен. Занятый логин → `409 login_taken`.
* `email` — обязателен и уникален. Занятая почта → `409 email_taken`.
* `password` — от 8 до 100 символов.
* `inviteToken` (необязательный) — приглашение из QR: пользователь создаётся и **сразу
  становится участником кампании** с ролью из приглашения, приглашение гасится.
  Недействительное или использованное приглашение → `409 invite_invalid` (пользователь
  при этом не создаётся).
* `platform: "android"` (необязательный) — долгая сессия: refresh-токен на 365 дней
  вместо 30.

### POST /api/auth/login
`{ "login": "gm", "password": "secret123", "platform": "android" }` → `200` (та же
схема). Ошибка → `401 unauthorized`. Регистр логина не важен.

### POST /api/auth/refresh
`{ "refreshToken": "…" }` → `200` `{ accessToken, refreshToken, expiresIn }`.
Старый refresh-токен инвалидируется (ротация). Срок новой сессии повторяет платформу
исходной (`web` → 30 дней, `android` → 365). Истёкший или отозванный токен → `401`.

### POST /api/auth/logout
`{ "refreshToken": "…" }` → `204`.

### POST /api/auth/password-reset/request
`{ "email": "gm@example.com" }` → `202` **всегда** (по ответу нельзя узнать, есть ли
такой адрес). Если пользователь есть:

* создаётся одноразовый токен на 1 час;
* в лог сервера пишется строка
  `СБРОС ПАРОЛЯ: login=gm email=gm@example.com ссылка=https://dnd.example.com/#/reset/<token>`;
* запрос попадает в админ-панель (ниже).

Почтовый сервер пока не настроен — отсюда запись в лог. Когда появится SMTP,
отправка письма включается одной настройкой.

### POST /api/auth/password-reset/confirm
`{ "token": "…", "newPassword": "…" }` → `204`. Пароль меняется, **все сессии
пользователя гасятся**. Неизвестный, использованный или просроченный токен →
`409 reset_invalid`.

### GET /api/admin/password-resets
Заголовок `X-Admin-Token: <DND_ADMIN_TOKEN>`. Если переменная `DND_ADMIN_TOKEN` не
задана, эндпоинт отвечает `404`; неверный токен → `401 unauthorized`.

```json
[ { "id": "…", "login": "gm", "email": "gm@example.com",
    "token": "S6z…", "requestedAt": "…", "expiresAt": "…" } ]
```

Это «админ-панель» без отдельной роли администратора: `token` — та же одноразовая
ссылка, что ушла в лог.

### GET /api/me · PATCH /api/me
`PATCH` принимает `{ "displayName": "…", "avatarFileId": "…", "email": "…", "phone": "+7…" }`.

* `email` — контакт для восстановления пароля; занятая почта → `409 email_taken`.
* `phone` — необязателен, уникален; нормализуется к виду `+7XXXXXXXXXX`, занятый
  телефон → `409 phone_taken`, пустая строка очищает значение. Пароль на телефон
  не сбрасывается.
* Профиль в ответе содержит `login`, `emailVerified`, `phone` и `hasPassword`.

### POST /api/me/password
`{ "currentPassword": "…", "newPassword": "…" }` → `204`. Текущий пароль обязателен
(пароль есть всегда, потому что вход только по паролю).

## 4. Кампании

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/campaigns` | кампании, где я участник |
| POST | `/api/campaigns` | создать (создатель → `OWNER`) |
| GET | `/api/campaigns/{id}` | детали + `myRole`, `myPermissions`, `members`, `retellings` |
| PATCH | `/api/campaigns/{id}` | `{title, description}` |
| DELETE | `/api/campaigns/{id}` | только `OWNER` |

Элемент списка:
```json
{ "id": "…", "title": "Курс на Фандалин", "description": "…", "myRole": "EDITOR_MASTER",
  "memberCount": 4, "entityCount": 27, "retellingCount": 3, "latestRevision": 128,
  "updatedAt": "2025-10-15T11:00:00.000Z" }
```

`entityCount` — сумма объектов всех четырёх классов (герои + персонажи + локации +
предметы). Поле `latestRevision` — точка отсчёта для синхронизации (`sync.md`).

Карточка `GET /api/campaigns/{id}` дополнительно отдаёт краткие списки рядом со
счётчиками:
```json
{ "id": "…", "title": "…", "ownerUserId": "…", "myRole": "OWNER",
  "myPermissions": ["campaign.manage", "…"],
  "memberCount": 4, "entityCount": 27, "retellingCount": 3, "latestRevision": 128,
  "members": [ { "id": "…", "userId": "…", "displayName": "Аня", "email": "…",
                 "role": "EDITOR_HERO", "linkedHeroId": "…", "linkedHeroName": "Лира Ксенофонт",
                 "status": "ACTIVE", "createdAt": "…" } ],
  "retellings": [ { "id": "…", "title": "Сессия 12", "sessionDate": "2025-10-12",
                    "revision": 43, "updatedAt": "…" } ],
  "createdAt": "…", "updatedAt": "…" }
```

## 5. Участники и приглашения

| Метод | Путь | Право |
|---|---|---|
| GET | `/api/campaigns/{id}/members` | `entity.read` |
| POST | `/api/campaigns/{id}/members` | `members.manage` |
| PATCH | `/api/campaigns/{id}/members/{memberId}` | `members.manage` |
| POST | `/api/campaigns/{id}/members/{memberId}/transfer-ownership` | `campaign.manage` |
| DELETE | `/api/campaigns/{id}/members/{memberId}` | `members.manage` |

`POST` тело:
```json
{ "email": "player@example.com", "role": "EDITOR_HERO", "linkedHeroId": "uuid-героя-или-null" }
```
Если пользователь с такой почтой уже есть — он сразу становится участником
(`status: ACTIVE`). Если нет — создаётся одноразовое приглашение, ответ содержит
`invite`.

`PATCH` — `{ "role": "…", "linkedHeroId": "…" }` (сменить роль/привязку героя).
Нельзя понизить последнего `OWNER` (`409 conflict`).

Участник:
```json
{ "id": "…", "userId": "…", "displayName": "Аня", "email": "…", "role": "EDITOR_HERO",
  "linkedHeroId": "…", "linkedHeroName": "Лира Ксенофонт", "status": "ACTIVE",
  "createdAt": "…" }
```

### Приглашения по QR

`POST /api/campaigns/{id}/invites` (право `invite.create`)
```json
{ "role": "EDITOR_HERO", "linkedHeroId": "…", "expiresInHours": 168 }
```
→ `201`
```json
{ "id": "…", "token": "7F3K9Q2M", "url": "https://dnd.example.com/join/7F3K9Q2M",
  "deepLink": "dndmemory://join?token=7F3K9Q2M",
  "qrPngBase64": "iVBORw0KGgo…", "role": "EDITOR_HERO",
  "linkedHeroId": "…", "linkedHeroName": "Лира Ксенофонт",
  "expiresAt": "…", "maxUses": 1, "uses": 0, "revoked": false, "valid": true }
```

* Приглашение **строго одноразовое**: `maxUses` всегда 1 и в запросе не передаётся.
  Принятие помечает его использованным, после чего `valid: false`.
* Срок жизни по умолчанию 7 дней (`expiresInHours` настраивается).
* `linkedHeroId` привязывает приглашённого к герою (герой — отдельная таблица).

| Метод | Путь | Что делает |
|---|---|---|
| GET | `/api/campaigns/{id}/invites` | список (право `invite.create`) |
| DELETE | `/api/campaigns/{id}/invites/{inviteId}` | отозвать |

`GET /api/invites/{token}` — **без авторизации**, публичный предпросмотр:
```json
{ "valid": true, "campaignId": "…", "campaignTitle": "Курс на Фандалин",
  "role": "EDITOR_HERO", "linkedHeroId": "…", "linkedHeroName": "Лира Ксенофонт",
  "expiresAt": "…" }
```

`POST /api/invites/{token}/accept` (нужна авторизация) → `{ "campaignId": "…",
"campaignTitle": "…", "memberId": "…", "role": "…", "linkedHeroId": "…" }`.
Недействительный, использованный или просроченный токен → `409 invite_invalid`.

Новый игрок может не входить отдельно: он передаёт токен в `POST /api/auth/register`
как `inviteToken` — регистрация и присоединение происходят одним запросом.

## 6. Объекты: четыре набора эндпоинтов

| Класс | Список | Создать | Чтение | Правка | Удаление |
|---|---|---|---|---|---|
| Герои | `GET /api/campaigns/{id}/heroes` | `POST` | `GET .../heroes/{heroId}` | `PATCH` | `DELETE` |
| Персонажи | `GET /api/campaigns/{id}/characters` | `POST` | `GET .../characters/{id}` | `PATCH` | `DELETE` |
| Локации | `GET /api/campaigns/{id}/locations` | `POST` | `GET .../locations/{id}` | `PATCH` | `DELETE` |
| Предметы | `GET /api/campaigns/{id}/items` | `POST` | `GET .../items/{id}` | `PATCH` | `DELETE` |

Общие правила:

* Параметры списка: `q` (поиск по строкам), `updatedSince` (ISO-8601),
  `limit` (по умолчанию 50, максимум 500), `offset`.
* Ответ списка — `{ "items": [ … ], "total": 27 }`.
* Права: героев создаёт, правит и удаляет **только владелец** (`hero.write`);
  персонажей, локации и предметы — владелец, редактор-мастер и редактор-герой
  (`entity.write`); читают все участники, включая гостя (`entity.read`).
* `PATCH` поддерживает `If-Match: <revision>`: при расхождении → `409 conflict`
  с текущим состоянием сервера в `details.server`.
* `POST` принимает необязательный `id`: если клиент задал идентификатор заранее,
  повторная отправка не создаст дубль.
* Старый общий `/api/campaigns/{id}/entities` удалён.

### Общие поля ответа

```json
{ "id": "…", "campaignId": "…", "name": "Лира Ксенофонт",
  "description": "Связана с [[Силдар Холлвинтер|мэром]] долгом…",
  "imageFileId": null,
  "avatarFileId": null,
  "links": [ { "targetId": "…", "label": "мэром", "resolved": true } ],
  "createdBy": "…", "createdByName": "Аня", "updatedBy": "…", "updatedByName": "Аня",
  "revision": 42, "createdAt": "…", "updatedAt": "…", "deleted": false }
```

`links` заполняет сервер: разбор `[[…]]` из `description`. Форматы:
`[[Имя объекта]]`, `[[Имя|подпись]]`, `[[#uuid|подпись]]`. Разрешение имени — по
всем четырём классам кампании, без учёта регистра; при неоднозначности выбирается
первый по алфавиту и `resolved: true`.

### Поля по классам

**Герой** (`heroes`)
```json
{ "race": "полуэльф", "klass": "следопыт",
  "playerUserId": "…", "playerName": "Аня",
  "items": [ { "id": "…", "name": "Меч справедливости", "itemId": "…",
               "quantity": 1, "notes": "подарок мэра" } ] }
```
`items` — предметы в котомке (`hero_items`). `itemId` заполняется, если название
нашлось среди предметов кампании (сопоставление по имени). В запросе на создание и
правку достаточно `{ "name": "…", "quantity": 1, "notes": "…" }`; переданный
`items` заменяет список целиком.

**Персонаж** (`characters`) — НПС
```json
{ "race": "человек", "shortDescription": "Мэр Фандалина" }
```

**Локация** (`locations`) — своих полей нет.

**Предмет** (`items`)
```json
{ "ownerName": "гильдия воров", "ownerHeroId": null, "ownerHeroName": null,
  "ownerCharacterId": "…", "ownerCharacterName": "Силдар Холлвинтер" }
```
`ownerName` — владелец «как написано в мире»; `ownerHeroId` или `ownerCharacterId` —
необязательная ссылка (не более одной, иначе `400 validation_failed`).

### Примеры запросов

```json
POST /api/campaigns/{id}/heroes
{ "name": "Лира Ксенофонт", "race": "полуэльф", "klass": "следопыт",
  "description": "Ищет брата.", "items": [ { "name": "Меч справедливости" } ] }
```
```json
PATCH /api/campaigns/{id}/characters/{characterId}
If-Match: 42
{ "shortDescription": "Мэр Фандалина, должен гильдии" }
```

### GET /api/campaigns/{id}/missing-names
`?text=…` — имена, на которые ссылаются в тексте, но которых в кампании ещё нет.
Интерфейс предлагает создать их. Право `entity.read`.

## 7. Пересказы

Состояния: `DRAFT` создаётся и живёт **только на устройстве**, на сервер не
отправляется; `PUBLISHED` — отправленный на сервер пересказ.

| Метод | Путь | Право |
|---|---|---|
| GET | `/api/campaigns/{id}/retellings?q=&authorEntityId=&limit=&offset=` | `retelling.read` |
| POST | `/api/campaigns/{id}/retellings` | `retelling.write` |
| GET | `/api/campaigns/{id}/retellings/{retellingId}` | `retelling.read` |
| PATCH | `/api/campaigns/{id}/retellings/{retellingId}` | автор, `retelling.write.own`, либо `retelling.publish` |
| DELETE | `/api/campaigns/{id}/retellings/{retellingId}` | автор или `campaign.manage` |

```json
{ "id": "…", "campaignId": "…", "clientId": "…", "title": "Сессия 12: подземелье",
  "body": "Мы вошли в [[Подземелье волн]]…", "authorEntityId": "…",
  "authorEntityName": "Лира Ксенофонт", "sessionDate": "2025-10-12",
  "status": "PUBLISHED", "links": [], "revision": 43,
  "createdBy": "…", "updatedBy": "…", "createdAt": "…", "updatedAt": "…", "deleted": false }
```

`authorEntityId` — герой или персонаж, от лица которого ведётся пересказ; имя
разрешается по всем четырём классам.

`POST`/`PATCH` со `status: "DRAFT"` → `422 draft_not_synced`
(«черновик хранится локально»). Это осознанное требование: черновики не покидают
устройство, пока автор не нажмёт «Опубликовать».

## 8. Поиск

`GET /api/campaigns/{id}/search?q=лира&kinds=HERO,CHARACTER,LOCATION,ITEM,RETELLING&limit=50`

Ищет по `name` и `description`; для персонажа дополнительно по `race` и
`shortDescription`; для пересказов — по заголовку и тексту. `kinds` не задан или
пуст — искать по всем классам. Право `entity.read`.

```json
{ "total": 3, "items": [
  { "kind": "HERO", "id": "…", "title": "Лира Ксенофонт",
    "snippet": "…<b>Лира</b> Ксенофонт, полуэльф…", "revision": 42, "updatedAt": "…" },
  { "kind": "RETELLING", "id": "…", "title": "Сессия 12",
    "snippet": "…вошли в подземелье вместе с <b>Лирой</b>…", "revision": 43, "updatedAt": "…" }
] }
```

Поиск сервера — регистронезависимый. Офлайн-клиенты ищут по своей локальной базе той
же логикой, поэтому поиск работает и без сети.

## 9. Синхронизация

Полное описание — `sync.md`. Кратко:

* `GET /api/campaigns/{id}/sync/changes?since=<revision>&limit=500&clientId=<установка>`
* `POST /api/campaigns/{id}/sync/changes`
* `GET /api/campaigns/{id}/sync/snapshot?limit=500&cursor=<opaque>`
* `GET /api/campaigns/{id}/sync/state` →
  `{ latestRevision, minRetainedRevision, entityCount, heroCount, characterCount,
     locationCount, itemCount, retellingCount, memberCount, pendingConflicts }`

`objectType` в журнале — `HERO`, `CHARACTER`, `LOCATION`, `ITEM`, `RETELLING`,
`CAMPAIGN`, `MEMBER`; `payload` — объект в форме DTO своего класса. При применении
правок сервер сначала ищет объект по `id`, затем — по ключу сопоставления класса
(герой, локация, предмет — по имени, персонаж — по имени и расе). Найденный по ключу
объект не дублируется, а правки ложатся в него: ответ получает `CONFLICT_RESOLVED`
и `conflict.reason = "matched_by_name"` (для персонажа `matched_by_name_and_race`).
Старое значение `ENTITY` не поддерживается: `REJECTED` с `unsupported_type`.

## 10. Файлы

* `POST /api/files` — `multipart/form-data`, поле `file` (≤ 10 МБ) → `201`
  `{ "id": "…", "url": "/api/files/{id}", "contentType": "image/png", "size": 12345 }`.
* `GET /api/files/{id}` — отдаёт содержимое (требуется авторизация).

## 11. Служебные

* `GET /api/health` → `{ "status": "UP", "version": "1.0.0", "time": "…" }` (без авторизации).
* `GET /api/version` → `{ "version": "1.0.0", "buildTime": "…", "profile": "prod" }`.

## 12. CORS и заголовки

* Разрешённые источники задаются переменной `DND_CORS_ORIGINS` (список через запятую).
* Для Android CORS не применяется (нет `Origin`).
* Все ответы содержат `X-Request-Id`; при `5xx` его нужно показывать в сообщении об ошибке.
* Разрешённые заголовки запроса: `Authorization`, `Content-Type`, `If-Match`,
  `X-Request-Id`, `X-Admin-Token`.
