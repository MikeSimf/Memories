# Синхронизация: офлайн-первый протокол

Задача: Android-приложение (и веб-версия) работают без сети, а при появлении
интернета сходятся с сервером без потери правок и без дубликатов.

## 1. Модель ревизий

* У каждой кампании на сервере есть счётчик `campaign.revision` (bigint, начинается с 0).
* Каждое изменение объекта увеличивает его на 1 и добавляет строку в журнал
  `change_log`:

```
change_log(campaign_id, revision, object_type, object_id, op, payload,
           actor_user_id, client_change_id, device_id, created_at)
  UNIQUE (campaign_id, revision)
  UNIQUE (campaign_id, client_change_id)   -- идемпотентность
```

* `object_type` — конкретный класс: `HERO`, `CHARACTER`, `LOCATION`, `ITEM`,
  `RETELLING`, `CAMPAIGN`, `MEMBER`. Общего `ENTITY` больше нет.
* `payload` — объект **в форме DTO своего класса**: те же поля, что отдаёт REST
  (`GET /api/campaigns/{id}/heroes/{heroId}` и его аналоги). Для `DELETE` —
  «надгробие»: `{ "id": "…", "objectType": "HERO", "deleted": true, "revision": …,
  "updatedAt": "…" }`.
* Клиент хранит `lastPulledRevision` и очередь своих неотправленных правок.

## 2. Идентификаторы

* Клиент генерирует UUID объекта сам (`objectId`) — тогда офлайн-созданная запись
  сразу имеет окончательный идентификатор, а сервер просто принимает его.
* `clientChangeId` — UUID конкретной правки, ключ идемпотентности: повторная
  отправка того же изменения после таймаута не создаёт вторую ревизию.
* `clientId` (поле в теле `push` и параметр `pull`) — идентификатор установки
  приложения. Сервер не отдаёт клиенту его собственные изменения (он их уже
  применил), но отдаёт изменения других устройств того же пользователя.
* Дубли ловятся не по `clientId` объекта (его в DTO v2 нет), а по **ключу
  сопоставления класса** — см. раздел 4.

## 3. Цикл синхронизации

```
1. PUSH  POST /api/campaigns/{id}/sync/changes
         тело: { clientId, changes: [ …локальные правки в порядке возникновения… ] }
2. сервер применяет их, присваивает ревизии, отвечает по каждой правке
3. клиент помечает правки как отправленные (или принимает решение по конфликту)
4. PULL  GET /api/campaigns/{id}/sync/changes?since={lastPulledRevision}&limit=500
5. применяет к локальной базе, lastPulledRevision = latestRevision
6. если hasMore = true — повторить шаг 4
```

Порядок «сначала push, потом pull» выбран специально: локальные правки получают
серверные ревизии, и следующий pull не вернёт их обратно.

### 3.1. Push

```http
POST /api/campaigns/{id}/sync/changes
```
```json
{
  "clientId": "install-8f2c…",
  "changes": [
    {
      "clientChangeId": "c-1111",
      "objectType": "ITEM",
      "objectId": "e-aaaa",
      "op": "UPSERT",
      "baseRevision": 41,
      "payload": {
        "id": "e-aaaa", "campaignId": "…", "name": "Меч справедливости",
        "description": "Найден в [[Подземелье волн]]…", "imageFileId": null,
        "ownerName": "гильдия воров", "ownerHeroId": null, "ownerCharacterId": null,
        "createdAt": "2025-10-15T10:00:00.000Z", "updatedAt": "2025-10-15T11:30:00.000Z"
      }
    },
    {
      "clientChangeId": "c-1112",
      "objectType": "HERO",
      "objectId": "h-bbbb",
      "op": "UPSERT",
      "baseRevision": 42,
      "payload": {
        "id": "h-bbbb", "campaignId": "…", "name": "Лира Ксенофонт",
        "race": "полуэльф", "klass": "следопыт", "playerUserId": null,
        "items": [ { "name": "Меч справедливости", "quantity": 1 } ],
        "updatedAt": "2025-10-15T11:31:00.000Z"
      }
    }
  ]
}
```
Ответ `200`:
```json
{
  "latestRevision": 43,
  "results": [
    { "clientChangeId": "c-1111", "objectId": "e-aaaa", "revision": 43,
      "status": "APPLIED", "conflict": null },
    { "clientChangeId": "c-1112", "objectId": "h-bbbb", "revision": 44,
      "status": "CONFLICT_RESOLVED",
      "conflict": { "reason": "matched_by_name", "serverRevision": 44 } }
  ]
}
```

`status`:

| Значение | Смысл | Что делает клиент |
|---|---|---|
| `APPLIED` | правка принята как есть (или повтор — идемпотентность) | снять флаг «грязный» |
| `CONFLICT_RESOLVED` | объект найден по ключу либо наша версия новее (`updatedAt`) | снять флаг, подменить локальный id серверным `objectId` |
| `REJECTED` | версия сервера новее или нет прав — правка отклонена | записать `conflict.server`, пометить локальную запись как «требует внимания», дать пользователю выбрать |

В `conflict` приходит состояние сервера:
```json
{ "reason": "stale_base", "serverRevision": 42,
  "server": { "id": "e-aaaa", "name": "Меч справедливости (старое)", "updatedAt": "…" } }
```

Правила конфликтов:

1. **Сначала по `id`, потом по ключу сопоставления.** Если объекта с таким `id` в
   кампании нет, сервер ищет объект того же класса с тем же ключом (раздел 4).
   Нашёл — новый **не создаётся**, правки ложатся в существующий, ответ получает
   `CONFLICT_RESOLVED` и `conflict.reason = "matched_by_name"` (для персонажа —
   `"matched_by_name_and_race"`). Клиент должен заменить свой локальный id на
   серверный `objectId` из ответа.
2. **Сравнение по `updatedAt`** (ISO-8601, UTC). Если база правки устарела
   (`baseRevision` меньше серверной ревизии), побеждает тот, чей `updatedAt` позже
   (last-write-wins на уровне объекта, без слияния полей): позже — `CONFLICT_RESOLVED`
   с `conflict.reason = "client_wins"`, раньше — `REJECTED` с `"stale_base"`.
3. **Удаление сильнее старой правки.** Если на сервере надгробие, а у клиента
   `updatedAt` старше — `REJECTED`, объект остаётся удалённым.
4. **Воскрешение.** Если правка новее надгробия — объект восстанавливается
   (`CONFLICT_RESOLVED`).
5. **Права проверяются до записи.** Нет прав → `REJECTED` с
   `conflict.reason = "forbidden"`, изменение не попадает в журнал. Героя может
   пушить только владелец кампании, поэтому push героя от игрока отклоняется.
6. **Неизвестный тип** (например, старый `ENTITY` из журналов прежней версии) →
   `REJECTED` с `conflict.reason = "unsupported_type"`.
7. Все конфликты дополнительно пишутся в таблицу `sync_conflicts` для разбора
   владельцем (диагностика, ручное разрешение).

### 3.2. Pull

```http
GET /api/campaigns/{id}/sync/changes?since=41&limit=500&clientId=install-8f2c
```

`clientId` (необязательный) — идентификатор установки, тот же, что в push. По нему
сервер не возвращает клиенту его собственные изменения: он их уже применил локально.
Если параметр не передан, отдаются все изменения подряд — это корректно, просто
клиент получит эхо своих правок.
```json
{
  "since": 41, "latestRevision": 43, "hasMore": false,
  "changes": [
    { "revision": 42, "objectType": "LOCATION", "objectId": "l-cccc", "op": "UPSERT",
      "payload": { "…": "полное состояние объекта в форме DTO локации" },
      "actorUserId": "…", "clientChangeId": "c-2222",
      "createdAt": "2025-10-15T11:10:00.000Z" },
    { "revision": 43, "objectType": "ITEM", "objectId": "e-aaaa", "op": "UPSERT",
      "payload": { "…": "…" }, "actorUserId": "…", "clientChangeId": "c-1111",
      "createdAt": "2025-10-15T11:30:00.000Z" }
  ]
}
```

* Изменения отдаются по возрастанию `revision`, свои (по `clientId` установки) исключаются.
* `hasMore: true` — клиент вызывает снова с `since = последний полученный revision`.
* Если `since` меньше минимальной сохранённой ревизии журнала (журнал компактится),
  сервер отвечает `410` с `{ "error": { "code": "resync_required" } }`; клиент делает
  полную перезагрузку: `GET /api/campaigns/{id}/sync/snapshot`.

### 3.3. Полная загрузка (первый вход, смена устройства, resync)

```http
GET /api/campaigns/{id}/sync/snapshot?limit=500&cursor=<opaque>
```
```json
{ "latestRevision": 128, "nextCursor": "…" | null,
  "heroes": [ … ], "characters": [ … ], "locations": [ … ], "items": [ … ],
  "retellings": [ … ], "members": [ … ], "campaign": { … } }
```

Страницы идут по порядку классов: герои, персонажи, локации, предметы, пересказы.
За одну страницу отдаётся один класс (до `limit` объектов), `nextCursor` указывает
следующую позицию. Клиент проходит по курсорам, затем сохраняет
`lastPulledRevision = latestRevision` и очищает очередь (её содержимое уже ушло на
сервер, поэтому перед snapshot выполняется push).

## 4. Ключ сопоставления (защита от дублей)

Каждый класс умеет отвечать, по какому признаку он сопоставляется с существующим
объектом:

| Класс | Ключ | `conflict.reason` при совпадении |
|---|---|---|
| `Hero` | имя | `matched_by_name` |
| `Character` | имя + раса | `matched_by_name_and_race` |
| `Location` | имя | `matched_by_name` |
| `Item` | имя | `matched_by_name` |

Сравнение — без учёта регистра, лишние пробелы схлопываются. Правило задано методом
самого класса, поэтому меняется одной строкой.

Зачем: два устройства офлайн заводят «Лиру Ксенофонт» — каждое со своим UUID.
При push второй объект не создаётся: сервер находит первого по имени, применяет
правки к нему и просит клиента заменить идентификатор.

## 5. Локальное состояние клиента

```
table heroes / characters / locations / items
                          : id, name, description, image_file_id, <свои поля>,
                            revision, updated_at, deleted, dirty(0/1), conflict_json(null)
table hero_items          : id, hero_id, name, item_id, quantity, notes, position
table retellings          : … + status(PUBLISHED)
table retelling_drafts    : только локально, в синхронизацию не попадает
table pending_changes     : client_change_id, object_type, object_id, op, base_revision,
                            payload_json, created_at, attempts
table sync_state          : campaign_id, last_pulled_revision, last_sync_at, last_error
```

Правила:

* **Черновики пересказов не покидают устройство.** Пока пересказ в состоянии
  `DRAFT`, он живёт в `retelling_drafts` и не попадает ни в `pending_changes`,
  ни в push. Нажатие «Опубликовать» переносит его в `pending_changes` как `UPSERT`
  с `status: "PUBLISHED"`. Сервер, получив `DRAFT`, отвечает `422 draft_not_synced`.
* Локальная правка: сразу пишется в объект, ставится `dirty = 1`, в
  `pending_changes` добавляется запись с `base_revision = revision` объекта.
* После `APPLIED`/`CONFLICT_RESOLVED` запись `pending_changes` удаляется, `dirty = 0`
  (при `matched_by_*` ещё и локальный id заменяется на серверный `objectId`).
* При `REJECTED` правка удаляется из очереди, объект помечается
  `conflict_json`, интерфейс показывает «есть расхождение с сервером» и даёт
  выбрать: оставить серверную версию или перезаписать своей (повторный push
  с новым `updatedAt`).

## 6. Когда запускается синхронизация

| Событие | Android | Веб |
|---|---|---|
| Старт приложения | `SyncWorker` при `onStart` | при загрузке страницы |
| Появление сети | `WorkManager` c `NetworkType.CONNECTED` | событие `online` |
| Локальная правка | отложенный воркер (debounce 3 с) | debounce 3 с |
| Вручную | pull-to-refresh | кнопка «Синхронизировать» |
| Периодически | периодический воркер раз в 15 минут | `setInterval` раз в 5 минут при открытой вкладке |

Веб-версия дополнительно регистрирует Service Worker (`vite-plugin-pwa`),
чтобы приложение открывалось и без сети (app shell + данные из IndexedDB).

## 7. Компактация журнала

* Журнал изменений кампании хранится 90 дней и не более 50 000 записей.
* Фоновая задача сервера (`MaintenanceJob`) раз в сутки удаляет старые записи;
  порог минимальной сохранённой ревизии публикуется в
  `GET /api/campaigns/{id}/sync/state`.
* Клиент, который не выходил на связь дольше этого срока, получает `resync_required`
  и перезагружается через snapshot — это нормальный сценарий, не ошибка.

## 8. Разрешение ссылок `[[…]]`

При каждом сохранении сервер пересчитывает `links`, разбирая `description` (у
персонажа — ещё и `shortDescription`, у пересказа — `body`). Имена ищутся по всем
четырём классам кампании: герои, персонажи, локации, предметы. Регистр не важен; при
неоднозначности берётся первый объект по алфавиту. Если имя не найдено — ссылка
остаётся в тексте, но в `links` попадает с `resolved: false` и `targetId: null`;
клиент рисует её как «битую» (пунктирное подчёркивание) и предлагает создать объект с
таким именем (`GET /api/campaigns/{id}/missing-names`).

Тем же сопоставлением по имени пользуется список предметов героя: `itemId`
заполняется, если название нашлось среди предметов кампании.

Клиенты офлайн разрешают ссылки по своей локальной базе — правило то же, поэтому
поведение без сети и с сетью совпадает.
