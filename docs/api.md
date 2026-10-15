# DnD Memory — HTTP API (v1)

Контракт между сервером, веб-версией и Android-приложением. Любое изменение здесь
означает синхронную правку всех трёх частей.

## 1. Общие правила

* Базовый путь: `/api`. Формат — JSON UTF-8 (`Content-Type: application/json`).
* Время — ISO-8601 UTC с миллисекундами: `2025-10-15T11:00:00.000Z`.
* Идентификаторы — UUID в виде строки.
* Аутентификация — `Authorization: Bearer <accessToken>` (JWT, 15 минут).
  Обновление — refresh-токен (30 дней, хранится на сервере, ротация при каждом обновлении).
* Ошибка:

```json
{ "error": { "code": "forbidden", "message": "Недостаточно прав", "details": {} } }
```

Коды: `unauthorized`, `forbidden`, `not_found`, `validation_failed`,
`conflict`, `draft_not_synced`, `invite_invalid`, `email_taken`, `rate_limited`,
`internal_error`.

* Пагинация — `limit` (по умолчанию 50, максимум 500) и `offset` (кроме синхронизации,
  где используется `since`).
* Все изменяющие запросы к объектам кампании увеличивают `revision` кампании на 1
  и пишут запись в журнал изменений (см. `sync.md`).

## 2. Перечисления

| Тип | Значения |
|---|---|
| `EntityType` | `HERO`, `NPC`, `LOCATION`, `ITEM` |
| `MemberRole` | `OWNER`, `EDITOR_MASTER`, `EDITOR_HERO`, `VIEWER` |
| `MemberStatus` | `INVITED`, `ACTIVE`, `REMOVED` |
| `RetellingStatus` | `DRAFT` (только локально), `PUBLISHED` |
| `ChangeOp` | `UPSERT`, `DELETE` |
| `ObjectType` | `ENTITY`, `RETELLING`, `CAMPAIGN`, `MEMBER` |

Права (`myPermissions`) — плоский список строк, вычисляется сервером для текущего
пользователя в кампании:

`campaign.manage`, `members.manage`, `invite.create`, `entity.read`,
`entity.write`, `entity.write.own`, `hero.write`, `retelling.read`,
`retelling.write`, `retelling.write.own`, `retelling.publish`.

Матрица ролей — в `roles.md`.

## 3. Аутентификация

### POST /api/auth/register
```json
{ "email": "gm@example.com", "password": "secret123", "displayName": "Мастер" }
```
→ `201`
```json
{ "user": { "id": "…", "email": "…", "displayName": "…", "avatarUrl": null },
  "accessToken": "…", "refreshToken": "…", "expiresIn": 900 }
```
Ошибки: `email_taken`, `validation_failed` (пароль ≥ 8 символов).

### POST /api/auth/login
`{ "email": "…", "password": "…" }` → `200` (та же схема). Ошибка `unauthorized`.

### POST /api/auth/refresh
`{ "refreshToken": "…" }` → `200` `{ accessToken, refreshToken, expiresIn }`.
Старый refresh-токен инвалидируется (ротация).

### POST /api/auth/logout
`{ "refreshToken": "…" }` → `204`.

### GET /api/me · PATCH /api/me
`PATCH` принимает `{ "displayName": "…", "avatarFileId": "…" }`.

### GET /api/auth/providers
```json
{ "yandex": { "enabled": true, "authorizeUrl": "/api/auth/oauth/yandex/authorize" },
  "vk":     { "enabled": false, "authorizeUrl": "/api/auth/oauth/vk/authorize" } }
```
Провайдер `enabled`, только если в конфигурации заданы `clientId`/`clientSecret`.

### OAuth (Яндекс ID, VK ID)
1. Клиент открывает `GET /api/auth/oauth/{provider}/authorize?redirectUri=<uri>&state=<random>`
   → `302` на страницу провайдера (для Android — в Custom Tab, `redirectUri`
   = `dndmemory://oauth`; для веба — адрес страницы `/oauth/callback`).
2. Провайдер возвращает код на `redirectUri`.
3. Клиент передаёт код на сервер:

```
POST /api/auth/oauth/{provider}/callback
{ "code": "…", "redirectUri": "…", "state": "…" }
```
→ `200` (та же схема, что у `/login`). Сервер сам обменивает код на токен,
получает профиль и создаёт/находит пользователя (связка по `provider + providerUserId`).

## 4. Кампании

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/campaigns` | кампании, где я участник |
| POST | `/api/campaigns` | создать (создатель → `OWNER`) |
| GET | `/api/campaigns/{id}` | детали + `myRole`, `myPermissions` |
| PATCH | `/api/campaigns/{id}` | `{title, description}` |
| DELETE | `/api/campaigns/{id}` | только `OWNER` |

Элемент списка:
```json
{ "id": "…", "title": "Курс на Фандалин", "description": "…", "myRole": "EDITOR_MASTER",
  "memberCount": 4, "entityCount": 27, "retellingCount": 3, "latestRevision": 128,
  "updatedAt": "2025-10-15T11:00:00.000Z" }
```
Поле `latestRevision` — точка отсчёта для синхронизации (`sync.md`).

## 5. Участники и приглашения

| Метод | Путь | Право |
|---|---|---|
| GET | `/api/campaigns/{id}/members` | `entity.read` |
| POST | `/api/campaigns/{id}/members` | `members.manage` |
| PATCH | `/api/campaigns/{id}/members/{memberId}` | `members.manage` |
| DELETE | `/api/campaigns/{id}/members/{memberId}` | `members.manage` |

`POST` тело:
```json
{ "email": "player@example.com", "role": "EDITOR_HERO", "linkedEntityId": "uuid-героя-или-null" }
```
Если пользователь с такой почтой уже есть — он сразу становится участником
(`status: ACTIVE`). Если нет — создаётся приглашение, ответ содержит `invite`.

`PATCH` — `{ "role": "…", "linkedEntityId": "…" }` (сменить роль/привязку героя).
Нельзя понизить последнего `OWNER`.

Участник:
```json
{ "id": "…", "userId": "…", "displayName": "Аня", "email": "…", "role": "EDITOR_HERO",
  "linkedEntityId": "…", "linkedEntityName": "Лира Ксенофонт", "status": "ACTIVE",
  "createdAt": "…" }
```

### Приглашения по QR
`POST /api/campaigns/{id}/invites`
```json
{ "role": "EDITOR_HERO", "linkedEntityId": "…", "expiresInHours": 168, "maxUses": 1 }
```
→ `201`
```json
{ "id": "…", "token": "7F3K9Q2M", "url": "https://dnd.example.com/join/7F3K9Q2M",
  "deepLink": "dndmemory://join?token=7F3K9Q2M",
  "qrPngBase64": "iVBORw0KGgo…", "role": "EDITOR_HERO", "linkedEntityId": "…",
  "expiresAt": "…", "maxUses": 1, "uses": 0 }
```

* `GET /api/campaigns/{id}/invites` — список; `DELETE /api/campaigns/{id}/invites/{inviteId}` — отозвать.
* `GET /api/invites/{token}` — **без авторизации**, публичный предпросмотр:
```json
{ "valid": true, "campaignId": "…", "campaignTitle": "Курс на Фандалин",
  "role": "EDITOR_HERO", "linkedEntityId": "…", "linkedEntityName": "Лира Ксенофонт",
  "expiresAt": "…" }
```
* `POST /api/invites/{token}/accept` (нужна авторизация) → `{ "campaignId": "…", "memberId": "…", "role": "…" }`.
  Недействительный или просроченный токен → `409 invite_invalid`.

## 6. Объекты (герои, НПС, локации, предметы)

| Метод | Путь | Право |
|---|---|---|
| GET | `/api/campaigns/{id}/entities?type=&q=&updatedSince=&limit=&offset=` | `entity.read` |
| POST | `/api/campaigns/{id}/entities` | `entity.write` (герои — `hero.write`) |
| GET | `/api/campaigns/{id}/entities/{entityId}` | `entity.read` |
| PATCH | `/api/campaigns/{id}/entities/{entityId}` | `entity.write` |
| DELETE | `/api/campaigns/{id}/entities/{entityId}` | `entity.write` |
| GET | `/api/campaigns/{id}/entities/{entityId}/backlinks` | `entity.read` |

Объект:
```json
{ "id": "…", "campaignId": "…", "clientId": "uuid-сгенерированный-клиентом",
  "type": "NPC", "name": "Силдар Холлвинтер", "summary": "Мэр Фандалина",
  "description": "Связан с [[Лира Ксенофонт|Лиры]] долгом…",
  "attrs": { "faction": "Совет Фандалина", "status": "alive" },
  "tags": ["город", "союзник"], "imageFileId": null,
  "links": [ { "targetId": "…", "label": "Лиры", "resolved": true } ],
  "createdBy": "…", "updatedBy": "…", "revision": 42,
  "createdAt": "…", "updatedAt": "…", "deleted": false }
```

* `clientId` — обязателен при офлайн-создании: сервер использует его для идемпотентности
  (повторная отправка того же `clientId` не создаёт дубликат).
* `links` заполняет сервер: разбор `[[…]]` из `description` и `summary`.
  Форматы: `[[Имя объекта]]`, `[[Имя|подпись]]`, `[[#uuid|подпись]]`.
  Разрешение имени — по кампании, без учёта регистра, при неоднозначности выбирается
  первый по алфавиту и `resolved: true`.
* `PATCH` может содержать `If-Match: <revision>`: при расхождении → `409 conflict`
  с текущим состоянием сервера в `details.server`.

### `attrs` по типам

| Тип | Поля |
|---|---|
| `HERO` | `playerName`, `race`, `klass`, `level`, `hp`, `ac`, `status` (`alive`/`dead`/`retired`) |
| `NPC` | `faction`, `status` (`alive`/`dead`/`unknown`), `locationEntityId` |
| `LOCATION` | `region`, `kind` (`city`/`dungeon`/`wild`/`building`), `parentEntityId` |
| `ITEM` | `kind` (`weapon`/`armor`/`potion`/`treasure`/`quest`), `ownerEntityId`, `locationEntityId`, `quantity`, `rarity` |

Сервер не отвергает лишние ключи в `attrs`, но перечисленные поля понимают все клиенты.

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

`POST`/`PATCH` со `status: "DRAFT"` → `422 draft_not_synced`
(«черновик хранится локально»). Это осознанное требование: черновики не покидают
устройство, пока автор не нажмёт «Опубликовать».

## 8. Поиск

`GET /api/campaigns/{id}/search?q=лира&types=HERO,NPC,LOCATION,ITEM,RETELLING&limit=50`
```json
{ "total": 3, "items": [
  { "kind": "ENTITY", "id": "…", "type": "HERO", "title": "Лира Ксенофонт",
    "snippet": "…<b>Лира</b> Ксенофонт, полуэльф…", "revision": 42, "updatedAt": "…" },
  { "kind": "RETELLING", "id": "…", "type": null, "title": "Сессия 12",
    "snippet": "…вошли в подземелье вместе с <b>Лирой</b>…", "revision": 43, "updatedAt": "…" }
] }
```
Поиск сервера — регистронезависимый по `name`, `summary`, `description`, `tags`
(и `body` пересказов). Офлайн-клиенты ищут по своей локальной базе той же логикой,
поэтому поиск работает и без сети.

## 9. Синхронизация

Полное описание — `sync.md`. Кратко:

* `GET /api/campaigns/{id}/sync/changes?since=<revision>&limit=500`
* `POST /api/campaigns/{id}/sync/changes`
* `GET /api/campaigns/{id}/sync/state` → `{ latestRevision, entityCount, retellingCount, memberCount }`

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
