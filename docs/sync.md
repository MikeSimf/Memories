# Синхронизация: офлайн-первый протокол

Задача: Android-приложение (и веб-версия) работают без сети, а при появлении
интернета сходятся с сервером без потери правок и без дубликатов.

## 1. Модель ревизий

* У каждой кампании на сервере есть счётчик `campaign.revision` (bigint, начинается с 0).
* Каждое изменение объекта (создание, правка, удаление) увеличивает его на 1 и
  добавляет строку в журнал `change_log`:

```
change_log(campaign_id, revision, object_type, object_id, op, payload, actor_user_id, client_change_id, created_at)
  PRIMARY KEY (campaign_id, revision)
  UNIQUE (campaign_id, client_change_id)   -- идемпотентность
```

* `payload` — полное состояние объекта после операции (для `DELETE` — «надгробие»:
  `{ "id": "…", "deleted": true, "updatedAt": "…" }`).
* Клиент хранит `lastPulledRevision` и очередь своих неотправленных правок.

## 2. Идентификаторы

* Клиент генерирует UUID объекта сам (`objectId`) — тогда офлайн-созданная запись
  сразу имеет окончательный идентификатор, а сервер просто принимает его.
* Дополнительно клиент шлёт `clientId` объекта (UUID создания): если запись
  случайно создана дважды на двух устройствах, сервер увидит одинаковый
  `clientId` и не продублирует (второй раз вернёт первый `objectId`).
* `clientChangeId` — UUID конкретной правки, ключ идемпотентности: повторная
  отправка того же изменения после таймаута не создаёт вторую ревизию.
* `clientId` устройства (поле `clientId` в теле запроса синхронизации) —
  идентификатор установки приложения. Сервер не отдаёт клиенту его собственные
  изменения (он их уже применил), но отдаёт изменения других устройств того же
  пользователя.

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
  "clientId": "device-8f2c…",
  "changes": [
    {
      "clientChangeId": "c-1111",
      "objectType": "ENTITY",
      "objectId": "e-aaaa",
      "op": "UPSERT",
      "baseRevision": 41,
      "payload": {
        "id": "e-aaaa", "campaignId": "…", "clientId": "e-aaaa",
        "type": "ITEM", "name": "Меч справедливости", "summary": "…",
        "description": "Найден в [[Подземелье волн]]…", "attrs": {}, "tags": [],
        "createdAt": "2025-10-15T10:00:00.000Z", "updatedAt": "2025-10-15T11:30:00.000Z"
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
      "status": "APPLIED", "conflict": null }
  ]
}
```

`status`:

| Значение | Смысл | Что делает клиент |
|---|---|---|
| `APPLIED` | правка принята как есть (или повтор — идемпотентность) | снять флаг «грязный» |
| `CONFLICT_RESOLVED` | была конфликтная база, но наша версия новее (`updatedAt`) — принята | снять флаг, показать уведомление «победа над версией другого» |
| `REJECTED` | версия сервера новее — правка отклонена | записать `conflict.server`, пометить локальную запись как «требует внимания», дать пользователю выбрать |

В `conflict` приходит состояние сервера:
```json
{ "reason": "stale_base", "serverRevision": 42,
  "server": { "id": "e-aaaa", "name": "Меч справедливости (старое)", "updatedAt": "…" } }
```

Правила конфликтов:

1. **Сравнение по `updatedAt`** (ISO-8601, UTC). Кто позже — тот прав
   (last-write-wins на уровне объекта, без слияния полей).
2. **Удаление сильнее старой правки.** Если на сервере надгробие, а у клиента
   `updatedAt` старше — `REJECTED`, объект остаётся удалённым.
3. **Воскрешение.** Если правка новее надгробия — объект восстанавливается
   (`CONFLICT_RESOLVED`).
4. **Права проверяются до записи.** Нет прав → `REJECTED` с
   `conflict.reason = "forbidden"`, изменение не попадает в журнал.
5. Все конфликты дополнительно пишутся в таблицу `sync_conflict` для разбора
   владельцем (диагностика, ручное разрешение).

### 3.2. Pull

```http
GET /api/campaigns/{id}/sync/changes?since=41&limit=500
```
```json
{
  "since": 41, "latestRevision": 43, "hasMore": false,
  "changes": [
    { "revision": 42, "objectType": "ENTITY", "objectId": "e-bbbb", "op": "UPSERT",
      "payload": { "…": "полное состояние объекта с revision 42" },
      "actorUserId": "…", "clientChangeId": "c-2222",
      "createdAt": "2025-10-15T11:10:00.000Z" },
    { "revision": 43, "objectType": "ENTITY", "objectId": "e-aaaa", "op": "UPSERT",
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
  "entities": [ … ], "retellings": [ … ], "members": [ … ], "campaign": { … } }
```
Клиент проходит по курсорам, затем сохраняет `lastPulledRevision = latestRevision`
и очищает очередь (её содержимое уже ушло на сервер, поэтому перед snapshot
выполняется push).

## 4. Локальное состояние клиента

```
table objects (entities)  : id, type, name, summary, description, attrs, tags, revision,
                            updated_at, deleted, dirty(0/1), conflict_json(null)
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
* После `APPLIED`/`CONFLICT_RESOLVED` запись `pending_changes` удаляется, `dirty = 0`.
* При `REJECTED` правка удаляется из очереди, объект помечается
  `conflict_json`, интерфейс показывает «есть расхождение с сервером» и даёт
  выбрать: оставить серверную версию или перезаписать своей (повторный push
  с новым `updatedAt`).

## 5. Когда запускается синхронизация

| Событие | Android | Веб |
|---|---|---|
| Старт приложения | `SyncWorker` при `onStart` | при загрузке страницы |
| Появление сети | `WorkManager` c `NetworkType.CONNECTED` | событие `online` |
| Локальная правка | отложенный воркер (debounce 3 с) | debounce 3 с |
| Вручную | pull-to-refresh | кнопка «Синхронизировать» |
| Периодически | периодический воркер раз в 15 минут | `setInterval` раз в 5 минут при открытой вкладке |

Веб-версия дополнительно регистрирует Service Worker (`vite-plugin-pwa`),
чтобы приложение открывалось и без сети (app shell + данные из IndexedDB).

## 6. Компактация журнала

* Журнал изменений кампании хранится 90 дней и не более 50 000 записей.
* Фоновая задача сервера (`SyncCompactionJob`) раз в сутки удаляет старые записи;
  порог минимальной сохранённой ревизии публикуется в `GET /api/campaigns/{id}/sync/state`.
* Клиент, который не выходил на связь дольше этого срока, получает `resync_required`
  и перезагружается через snapshot — это нормальный сценарий, не ошибка.

## 7. Разрешение ссылок `[[…]]`

При каждом сохранении объекта сервер пересчитывает `links`, разбирая `summary`
и `description`. Имена ищутся по всей кампании (регистр не важен). Если имя не
найдено — ссылка остаётся в тексте, но в `links` попадает с `resolved: false`
и `targetId: null`; клиент рисует её как «битую» (пунктирное подчёркивание) и
предлагает создать объект с таким именем.

Клиенты офлайн разрешают ссылки по своей локальной базе — правило то же, поэтому
поведение без сети и с сетью совпадает.
