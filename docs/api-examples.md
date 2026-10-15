# Ручная проверка API (curl)

Короткий сценарий: от регистрации до приглашения по QR. Выполняется за пару минут
на тестовом контуре и сразу показывает, что сервер, база и миграции в порядке.

```bash
BASE=http://localhost:8081          # тестовый контур; для рабочего — https://ваш-домен
```

## 1. Здоровье и вход

```bash
curl -s $BASE/api/health
# {"status":"UP","version":"1.0.0","time":"..."}

curl -s -X POST $BASE/api/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"email":"gm@example.com","password":"secret123","displayName":"Мастер"}' | tee /tmp/auth.json
```

Дальше удобно достать токен в переменную:

```bash
TOKEN=$(sed -E 's/.*"accessToken":"([^"]+)".*/\1/' /tmp/auth.json)
AUTH="Authorization: Bearer $TOKEN"
```

Проверка профиля:

```bash
curl -s $BASE/api/me -H "$AUTH"
```

## 2. Кампания

```bash
curl -s -X POST $BASE/api/campaigns -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"Курс на Фандалин","description":"Наша первая кампания"}' | tee /tmp/campaign.json

CID=$(sed -E 's/.*"id":"([^"]+)".*/\1/' /tmp/campaign.json)
```

Текущая ревизия — точка отсчёта синхронизации:

```bash
curl -s $BASE/api/campaigns/$CID/sync/state -H "$AUTH"
# {"latestRevision":1, ...}
```

## 3. Объекты и живые ссылки

Создаём героя и локацию, а в описании ссылаемся на героя:

```bash
curl -s -X POST $BASE/api/campaigns/$CID/entities -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"type":"HERO","name":"Лира Ксенофонт","summary":"Полуэльф, следопыт",
       "attrs":{"playerName":"Аня","klass":"Следопыт","level":3}}'

curl -s -X POST $BASE/api/campaigns/$CID/entities -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"type":"LOCATION","name":"Фандалин","summary":"Городок у Трибоара",
       "description":"Здесь живёт [[Лира Ксенофонт|Лира]] и [[Силдар Холлвинтер]]."}'
```

В ответе на локацию смотрим поле `links`:

```json
"links": [
  { "targetId": "…", "label": "Лира", "resolved": true },
  { "targetId": null, "label": "Силдар Холлвинтер", "resolved": false }
]
```

Так выполняется требование «наименование в описании — гиперссылка на карточку»:
разрешённые ссылки ведут на объект, неразрешённые клиенты рисуют пунктиром и
предлагают создать объект с таким именем.

## 4. Поиск

```bash
curl -s "$BASE/api/campaigns/$CID/search?q=лира" -H "$AUTH"
curl -s "$BASE/api/campaigns/$CID/entities?type=HERO&q=лира" -H "$AUTH"
```

## 5. Черновик пересказа отклоняется

Это проверка требования «черновик не отправляется на сервер»:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/campaigns/$CID/retellings \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"Сессия 1","body":"…","status":"DRAFT"}'
# 422

curl -s -X POST $BASE/api/campaigns/$CID/retellings \
  -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"Сессия 1","body":"Мы вошли в [[Фандалин]].","status":"PUBLISHED"}'
# 201
```

## 6. Синхронизация: push и pull

Отправляем офлайн-правку (предмет, придуманный без сети):

```bash
OBJ=$(uuidgen)
curl -s -X POST $BASE/api/campaigns/$CID/sync/changes -H "$AUTH" -H 'Content-Type: application/json' -d @- <<JSON
{
  "clientId": "device-test-1",
  "changes": [{
    "clientChangeId": "c-1",
    "objectType": "ENTITY",
    "objectId": "$OBJ",
    "op": "UPSERT",
    "baseRevision": 0,
    "payload": {
      "id": "$OBJ", "clientId": "$OBJ", "type": "ITEM",
      "name": "Меч справедливости", "summary": "Найден в [[Фандалин|Фандалине]]",
      "updatedAt": "$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
    }
  }]
}
JSON
```

Повторите тот же запрос ещё раз — ответ снова будет `APPLIED` с той же ревизией:
сработала идемпотентность по `clientChangeId`, дубликата не появилось.

Забираем изменения как «другое устройство»:

```bash
curl -s "$BASE/api/campaigns/$CID/sync/changes?since=0&clientId=device-test-2" -H "$AUTH"
```

Первичная загрузка целиком:

```bash
curl -s "$BASE/api/campaigns/$CID/sync/snapshot" -H "$AUTH"
```

## 7. Приглашение по QR

```bash
curl -s -X POST $BASE/api/campaigns/$CID/invites -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"role":"EDITOR_HERO","expiresInHours":168,"maxUses":1}' | tee /tmp/invite.json

TOK=$(sed -E 's/.*"token":"([^"]+)".*/\1/' /tmp/invite.json)
```

* `url` — ссылка для веба, `deepLink` — для Android, `qrPngBase64` — готовый QR.
* Предпросмотр без авторизации: `curl -s $BASE/api/invites/$TOK`
* Принятие приглашения (нужен вход): `curl -s -X POST $BASE/api/invites/$TOK/accept -H "$AUTH"`

Сохранить QR в файл:

```bash
sed -E 's/.*"qrPngBase64":"([^"]+)".*/\1/' /tmp/invite.json | base64 -d > qr.png
```

## 8. Роли

Второй пользователь, приглашённый как `VIEWER`, не должен ничего менять:

```bash
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"guest@example.com","password":"secret123","displayName":"Гость"}' > /tmp/guest.json
GUEST=$(sed -E 's/.*"accessToken":"([^"]+)".*/\1/' /tmp/guest.json)

curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/campaigns/$CID/entities \
  -H "Authorization: Bearer $GUEST" -H 'Content-Type: application/json' \
  -d '{"type":"ITEM","name":"Не должно создаться"}'
# 403
```

## Если что-то не так

| Ответ | Что смотреть |
|---|---|
| `401 unauthorized` | токен истёк (15 минут) или не передан заголовок `Authorization` |
| `403 forbidden` | роль участника не даёт праву — см. [`roles.md`](roles.md) |
| `409 conflict` | объект изменён другим участником; в `error.details.server` лежит серверная версия |
| `410 resync_required` | журнал компактизован, нужна полная загрузка через `snapshot` |
| `500 internal_error` | в логах сервера строка с тем же `X-Request-Id`, что в ответе |

Логи: `docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml logs -f server`
