# Переработка v2: авторизация по логину и четыре класса объектов

Документ фиксирует решения и является заданием на реализацию. После неё
[`api.md`](api.md) должен быть обновлён под новое состояние.

## 1. Авторизация

### Что убираем
Вход через **Яндекс и VK удаляется полностью**: `OAuthService`, эндпоинты
`/api/auth/oauth/*`, `GET /api/auth/providers`, секция `dnd.oauth` в настройках,
поля `users.provider` и `users.provider_user_id`. Провайдеров больше нет — вход
только по логину и паролю.

### Модель пользователя
```
users: id, login (уникальный), email (уникальный, обязательный),
       email_verified (по умолчанию false), phone (уникальный, необязательный),
       display_name, password_hash, avatar_file_id, created_at, updated_at
```

* `login` — короткое имя для входа: 3–32 символа из `a–z`, `0–9`, `_`, `.`, `-`.
  Хранится в нижнем регистре, регистр при входе не важен.
* `email` — обязателен при регистрации. Нужен для сброса пароля.
* `phone` — необязателен, уникален. На него пароль не сбрасывается (SMS нет).

### Эндпоинты

| Метод | Путь | Тело | Ответ |
|---|---|---|---|
| POST | `/api/auth/register` | `{login, email, password, displayName, inviteToken?}` | `201` + токены |
| POST | `/api/auth/login` | `{login, password}` | `200` + токены |
| POST | `/api/auth/refresh` | `{refreshToken}` | `200` + токены |
| POST | `/api/auth/logout` | `{refreshToken}` | `204` |
| POST | `/api/auth/password-reset/request` | `{email}` | `202` всегда |
| POST | `/api/auth/password-reset/confirm` | `{token, newPassword}` | `204` |
| GET | `/api/me` | — | профиль |
| PATCH | `/api/me` | `{displayName?, email?, phone?, avatarFileId?}` | профиль |
| POST | `/api/me/password` | `{currentPassword?, newPassword}` | `204` |

`inviteToken` в регистрации — приглашение из QR: пользователь создаётся и **сразу
присоединяется к кампании** с ролью из приглашения, приглашение гасится.

Токены: access 15 минут; refresh для веб-версии 30 дней, для приложения — 365 дней
(приложение передаёт `platform: "android"` в запросе на вход/регистрацию, чтобы
получить долгую сессию; иначе 30 дней). Refresh-токен хранится хешем, ротация при
каждом обновлении, отзыв — как сейчас.

### Сброс пароля без SMTP

Почтовый сервер пока не настроен, поэтому:

1. `POST /api/auth/password-reset/request` всегда отвечает `202` (не раскрываем,
   есть ли такой адрес). Если пользователь есть — создаётся одноразовый токен на
   1 час, ссылка **пишется в лог** сервера строкой:
   `СБРОС ПАРОЛЯ: login=<login> email=<email> ссылка=<DND_PUBLIC_URL>/#/reset/<token>`.
2. Дополнительно запрос виден администратору сервера:
   `GET /api/admin/password-resets` — список ожидающих сбросов
   (`{id, login, email, token, requestedAt, expiresAt}`).
   Доступ только с заголовком `X-Admin-Token: <DND_ADMIN_TOKEN>`; если переменная
   `DND_ADMIN_TOKEN` не задана, эндпоинт отвечает `404`. Это «админ-панель» без
   отдельной роли администратора.
3. `POST /api/auth/password-reset/confirm` принимает токен, меняет пароль и гасит все
   сессии пользователя.

Когда появится SMTP — отправка письма включается одной настройкой, остальной код готов.

### Приглашения: строго одноразовые

* `max_uses` больше не настраивается: приглашение всегда одноразовое
  (`max_uses = 1`), при принятии помечается использованным.
* Срок жизни по умолчанию 7 дней, настраивается.
* `linked_hero_id` — приглашённый привязывается к герою (герой теперь отдельная
  таблица).
* Получив QR, пользователь либо **регистрируется** (логин + пароль + почта), либо
  **входит** существующим аккаунтом; в обоих случаях приглашение гасится.
* Режим `DEVICE`, таблица `devices`, эндпоинты `/api/devices/*` и
  `/api/campaigns/{id}/devices/*` **удаляются** — они противоречат пункту «новый
  пользователь придумывает логин и пароль».

## 2. Классы объектов

Вместо одной таблицы `entities` — четыре класса, каждый в своей таблице. Общие части
описаны **независимыми интерфейсами-экстендерами**, а не цепочкой наследования:
класс объявляет ровно те контракты, которые ему нужны.

```
interface GameEntityExtender   id, campaignId
interface NamedExtender        name, description, matchKey(), matchReason()
interface SyncExtended         deleted, createdAt, updatedAt, createdBy, updatedBy, revision
interface ImageExtender        imageFileId

@Entity Hero       extends GameEntitySupport implements все четыре + race, klass, playerUserId
@Entity Character  extends GameEntitySupport implements все четыре + race, shortDescription
@Entity Location   extends GameEntitySupport implements все четыре (ничего своего)
@Entity Item       extends GameEntitySupport implements все четыре + ownerName, ownerHeroId, ownerCharacterId
```

Java не поддерживает множественное наследование полей, поэтому общее состояние
объявлено один раз в `@MappedSuperclass GameEntitySupport`, который реализует все
четыре интерфейса. Контракты при этом остаются раздельными: новый класс может
реализовать любое подмножество, а новое общее поле добавляется одной правкой в
`GameEntitySupport` и одной миграцией.

### Ключ сопоставления (защита от дублей при синхронизации)

Каждый класс умеет отвечать, по какому признаку он сопоставляется с уже
существующим объектом:

| Класс | Ключ |
|---|---|
| `Hero` | `name` |
| `Character` | `name` + `race` |
| `Location` | `name` |
| `Item` | `name` |

Правило задаётся в самом классе (метод возвращает нормализованный ключ), поэтому
изменить его — одна строка. При синхронизации, если объект с таким ключом в кампании
уже есть, новый **не создаётся**: правки ложатся в существующий, ответ помечается
`CONFLICT_RESOLVED` с `conflict.reason = "matched_by_name"` (для персонажа —
`"matched_by_name_and_race"`).

### Поля по классам

**Hero** (добавляет только **владелец** кампании)
* `race`, `klass` — строки;
* `playerUserId` — кто отыгрывает (ссылка на пользователя, необязательна);
* предметы: список `hero_items` — `{id, name, itemId?, quantity?, notes?}`, где
  `itemId` заполнен, если название нашлось среди предметов кампании (сопоставление по
  имени, как у ссылок `[[…]]`).

**Character** (НПС)
* `race`, `shortDescription` — поиск идёт ещё и по ним.

**Location**
* своих полей нет.

**Item**
* `ownerName` — строка-владелец (как написано в мире);
* `ownerHeroId` или `ownerCharacterId` — необязательная ссылка на героя/персонажа
  (не более одной).

### Ссылки в описании

Разметка `[[Имя]]`, `[[Имя|подпись]]`, `[[#id|подпись]]` в `description` работает как
раньше; разрешение имён — по всем четырём классам кампании.

## 3. API

### Кампания

`GET /api/campaigns/{id}` возвращает детализацию: `members` (краткий список
участников) и `retellings` (краткий список пересказов) — помимо счётчиков.

### Объекты — отдельные эндпоинты на класс

| Класс | Список | Создать | Чтение | Правка | Удаление |
|---|---|---|---|---|---|
| Герои | `GET /api/campaigns/{id}/heroes` | `POST` | `GET .../heroes/{heroId}` | `PATCH` | `DELETE` |
| Персонажи | `GET /api/campaigns/{id}/characters` | `POST` | `GET .../characters/{id}` | `PATCH` | `DELETE` |
| Локации | `GET /api/campaigns/{id}/locations` | `POST` | `GET .../locations/{id}` | `PATCH` | `DELETE` |
| Предметы | `GET /api/campaigns/{id}/items` | `POST` | `GET .../items/{id}` | `PATCH` | `DELETE` |

* Общие параметры списка: `q`, `updatedSince`, `limit`, `offset`.
* В описаниях ответов — общие поля (`id`, `campaignId`, `name`, `description`,
  `imageFileId`, `links`, `createdBy`, `updatedBy`, `revision`, `createdAt`,
  `updatedAt`, `deleted`) плюс поля своего класса.
* `PATCH` поддерживает `If-Match: <revision>` как раньше.
* `/api/campaigns/{id}/entities` из прежней версии **удаляется**.

### Поиск

`GET /api/campaigns/{id}/search?q=&kinds=HERO,CHARACTER,LOCATION,ITEM,RETELLING&limit=`
ищет по `name` и `description`; для персонажа дополнительно по `race` и
`shortDescription`; для пересказов — по заголовку и тексту.

### Синхронизация

* `objectType` в журнале: `HERO`, `CHARACTER`, `LOCATION`, `ITEM`, `RETELLING`,
  `CAMPAIGN`, `MEMBER`.
* `payload` — объект в форме своего DTO, как в push, так и в pull.
* Применение правок: найти объект по `id`, при отсутствии — по ключу сопоставления
  (см. выше); дальше обычные правила конфликтов из [`sync.md`](sync.md).
* Старые значения `ENTITY` в журнале не поддерживаем: развёрнутых данных нет.

### Права

| Действие | Кто |
|---|---|
| Создание, правка, удаление **героя** | только владелец кампании |
| Создание, правка, удаление персонажа, локации, предмета | владелец, редактор-мастер, редактор-герой |
| Просмотр всего | все участники, включая гостя |
| Участники, приглашения, настройки кампании | владелец (приглашения — `members.manage`) |

## 4. Миграция базы

Развёрнутых данных нет, поэтому миграция `V3__classes_and_login.sql` делает чистую
схему:

1. `users`: добавить `login` (уникальный), `email_verified`; сделать `email`
   обязательным и уникальным; удалить `provider`, `provider_user_id`.
2. Удалить таблицу `devices` и колонки `invites.mode`, `invites.display_name`,
   `invites.label`; `invites.max_uses` зафиксировать значением 1.
3. Удалить таблицу `entities`.
4. Создать `heroes`, `characters`, `locations`, `items` с общими колонками
   (`id, campaign_id, name, description, image_file_id, deleted, created_at,
   updated_at, created_by, updated_by, revision`) и своими полями.
5. Создать `hero_items` (`id, hero_id, name, item_id, quantity, notes, position`).
6. Создать `password_reset_requests` (`id, user_id, token_hash, expires_at, used,
   created_at`) и `refresh_tokens.platform` (чтобы отличать приложение от веба).
7. `members.linked_entity_id` переименовать в `linked_hero_id`.

После миграции `node scripts/check-schema.mjs` должен показывать согласованность:
это единственная автоматическая проверка схемы в проекте.

## 5. Что меняется у клиентов

* Экран входа: логин + пароль, ссылка «Забыли пароль?» и «У меня есть приглашение».
* Регистрация: логин, почта, пароль, имя; при переходе по QR — сразу с приглашением.
* Кнопки Яндекс/VK убрать.
* Карточки: настоящие поля вместо `attrs`; у героя — раса, класс, игрок и список
  предметов; у персонажа — раса и краткое описание; у предмета — владелец со ссылкой.
* Три вкладки сохраняются: Персонажи (герои и НПС), Локации, Предметы.
* Синхронизация: `objectType` теперь конкретный класс, `payload` — своя форма на класс.
