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
  -d '{"login":"gm","email":"gm@example.com","password":"secret123","displayName":"Мастер"}' \
  | tee /tmp/auth.json
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

Вход тем же логином в другом регистре (`GM`) работает так же — логин хранится
в нижнем регистре.

## 2. Кампания

```bash
curl -s -X POST $BASE/api/campaigns -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"title":"Курс на Фандалин","description":"Наша первая кампания"}' | tee /tmp/campaign.json

CID=$(sed -E 's/.*"id":"([^"]+)".*/\1/' /tmp/campaign.json)
```

Текущая ревизия — точка отсчёта синхронизации:

```bash
curl -s $BASE/api/campaigns/$CID/sync/state -H "$AUTH"
# {"latestRevision":1, "heroCount":0, "characterCount":0, ...}
```

Карточка кампании отдаёт счётчики и краткие списки участников и пересказов:

```bash
curl -s $BASE/api/campaigns/$CID -H "$AUTH"
```

## 3. Объекты и живые ссылки

Создаём героя и локацию, а в описании ссылаемся на героя. Героев заводит только
владелец кампании:

```bash
curl -s -X POST $BASE/api/campaigns/$CID/heroes -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Лира Ксенофонт","race":"полуэльф","klass":"следопыт",
       "description":"Ищет брата.","items":[{"name":"Меч справедливости","quantity":1}]}'

curl -s -X POST $BASE/api/campaigns/$CID/locations -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"name":"Фандалин","description":"Здесь живёт [[Лира Ксенофонт|Лира]] и [[Силдар Холлвинтер]]."}'
```

В ответе на локацию смотрим поле `links`:

```json
"links": [
  { "targetId": "…", "label": "Лира", "resolved": true },
  { "targetId": null, "label": "Силдар Холлвинтер", "resolved": false }
]
```

Так выполняется требование «наименование в описании — гиперссылка на карточку»:
разрешённые ссылки ведут на объект (имя ищется по всем четырём классам),
неразрешённые клиенты рисуют пунктиром и предлагают создать объект с таким именем.

Правка с проверкой ревизии:

```bash
curl -s -X PATCH $BASE/api/campaigns/$CID/locations/$LID \
  -H "$AUTH" -H 'If-Match: 42' -H 'Content-Type: application/json' \
  -d '{"description":"Городок у Трибоара."}'
# 409 conflict, если ревизия уже другая: сервер вернёт details.server
```

## 4. Поиск

```bash
curl -s "$BASE/api/campaigns/$CID/search?q=лира" -H "$AUTH"
curl -s "$BASE/api/campaigns/$CID/search?q=лира&kinds=HERO,LOCATION" -H "$AUTH"
curl -s "$BASE/api/campaigns/$CID/heroes?q=лира&limit=50" -H "$AUTH"
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

Отправляем офлайн-правку (предмет, придуманный без сети). `payload` — форма DTO
своего класса, `objectType` — конкретный класс:

```bash
OBJ=$(uuidgen)
curl -s -X POST $BASE/api/campaigns/$CID/sync/changes -H "$AUTH" -H 'Content-Type: application/json' -d @- <<JSON
{
  "clientId": "install-test-1",
  "changes": [{
    "clientChangeId": "c-1",
    "objectType": "ITEM",
    "objectId": "$OBJ",
    "op": "UPSERT",
    "baseRevision": 0,
    "payload": {
      "id": "$OBJ", "name": "Меч справедливости",
      "description": "Найден в [[Фандалин|Фандалине]]",
      "updatedAt": "$(date -u +%Y-%m-%dT%H:%M:%S.000Z)"
    }
  }]
}
JSON
```

Повторите тот же запрос ещё раз — ответ снова будет `APPLIED` с той же ревизией:
сработала идемпотентность по `clientChangeId`, дубликата не появилось.

Если отправить другой `objectId`, но то же имя, что у существующего предмета,
сервер не создаст дубль: ответ будет `CONFLICT_RESOLVED` с
`conflict.reason = "matched_by_name"`, а правки лягут в существующий объект.

Забираем изменения как «другое устройство»:

```bash
curl -s "$BASE/api/campaigns/$CID/sync/changes?since=0&clientId=install-test-2" -H "$AUTH"
```

Первичная загрузка целиком (страницы по классам, курсор в `nextCursor`):

```bash
curl -s "$BASE/api/campaigns/$CID/sync/snapshot" -H "$AUTH"
```

## 7. Приглашение по QR

```bash
curl -s -X POST $BASE/api/campaigns/$CID/invites -H "$AUTH" -H 'Content-Type: application/json' \
  -d '{"role":"EDITOR_HERO","expiresInHours":168}' | tee /tmp/invite.json

TOK=$(sed -E 's/.*"token":"([^"]+)".*/\1/' /tmp/invite.json)
```

* Приглашение всегда одноразовое: `maxUses` в запросе не передаётся, в ответе он
  равен 1, а после принятия `valid` становится `false`.
* `url` — ссылка для веба, `deepLink` — для Android, `qrPngBase64` — готовый QR.
* Предпросмотр без авторизации: `curl -s $BASE/api/invites/$TOK`
* Принятие приглашения (нужен вход): `curl -s -X POST $BASE/api/invites/$TOK/accept -H "$AUTH"`

Новый игрок может не входить отдельно — приглашение подхватывается при регистрации:

```bash
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d "{\"login\":\"anya\",\"email\":\"anya@example.com\",\"password\":\"secret123\",
       \"displayName\":\"Аня\",\"inviteToken\":\"$TOK\",\"platform\":\"android\"}"
# 201: пользователь создан и сразу участник кампании, приглашение погашено
```

Сохранить QR в файл:

```bash
sed -E 's/.*"qrPngBase64":"([^"]+)".*/\1/' /tmp/invite.json | base64 -d > qr.png
```

## 8. Роли

Второй пользователь, приглашённый как `VIEWER`, не должен ничего менять:

```bash
curl -s -X POST $BASE/api/auth/register -H 'Content-Type: application/json' \
  -d '{"login":"guest","email":"guest@example.com","password":"secret123","displayName":"Гость"}' \
  > /tmp/guest.json
GUEST=$(sed -E 's/.*"accessToken":"([^"]+)".*/\1/' /tmp/guest.json)

curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/campaigns/$CID/items \
  -H "Authorization: Bearer $GUEST" -H 'Content-Type: application/json' \
  -d '{"name":"Не должно создаться"}'
# 403
```

Проверка, что герои закрыты и для редактора-мастера:

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/campaigns/$CID/heroes \
  -H "Authorization: Bearer $MASTER" -H 'Content-Type: application/json' \
  -d '{"name":"Чужой герой"}'
# 403: героев ведёт только владелец
```

## 9. Сброс пароля без SMTP

```bash
curl -s -o /dev/null -w '%{http_code}\n' -X POST $BASE/api/auth/password-reset/request \
  -H 'Content-Type: application/json' -d '{"email":"gm@example.com"}'
# 202 — всегда, даже если такого адреса нет

docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml \
  logs --since 5m server | grep 'СБРОС ПАРОЛЯ'
# СБРОС ПАРОЛЯ: login=gm email=gm@example.com ссылка=http://localhost:8081/#/reset/<token>

curl -s -X POST $BASE/api/auth/password-reset/confirm -H 'Content-Type: application/json' \
  -d '{"token":"<token>","newPassword":"newsecret123"}'
# 204; все сессии пользователя погашены
```

Список ожидающих сбросов (нужна переменная `DND_ADMIN_TOKEN`; без неё — `404`):

```bash
curl -s -H "X-Admin-Token: $DND_ADMIN_TOKEN" $BASE/api/admin/password-resets
```

## Если что-то не так

| Ответ | Что смотреть |
|---|---|
| `401 unauthorized` | токен истёк (15 минут) или не передан заголовок `Authorization` |
| `403 forbidden` | роль участника не даёт праву — см. [`roles.md`](roles.md) |
| `409 conflict` | объект изменён другим участником; в `error.details.server` лежит серверная версия |
| `409 login_taken` / `email_taken` / `phone_taken` | логин, почта или телефон уже заняты |
| `409 invite_invalid` | приглашение использовано, отозвано или просрочено |
| `409 reset_invalid` | ссылка сброса пароля использована или устарела (живёт час) |
| `410 resync_required` | журнал компактизован, нужна полная загрузка через `snapshot` |
| `500 internal_error` | в логах сервера строка с тем же `X-Request-Id`, что в ответе |

Логи: `docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml logs -f server`
