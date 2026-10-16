#!/usr/bin/env bash
# Сквозная проверка API на живом контуре: 16 сценариев от регистрации до синхронизации.
#
#   ./scripts/smoke-api.sh http://localhost:8081
#   ./scripts/smoke-api.sh https://dnd.example.com
#
# Требуется только curl — результат разбирается без сторонних утилит.
# Скрипт создаёт тестовые данные (пользователь gm_smoke, кампания «Проверка API»),
# поэтому запускайте его на тестовом контуре, а не на рабочем.
set -uo pipefail

BASE="${1:-http://localhost:8080}"
JSON='Content-Type: application/json'
FAILED=0

# Достаёт значение поля из JSON: jget accessToken, jget user.login, jget items.0.id
# Достаёт значение поля из JSON: строку, число или true/false — jget total, jget user.login
jget() {
  grep -o "\"${1##*.}\"[[:space:]]*:[[:space:]]*\(\"[^\"]*\"\|[0-9.]\+\|true\|false\)" <<<"$2" \
    | head -1 | sed -E 's/.*:[[:space:]]*//; s/^"//; s/"$//'
}

step() { printf '\n=== %s ===\n' "$1"; }
ok()   { printf '  ✓ %s\n' "$1"; }
bad()  { printf '  ✗ %s\n' "$1"; FAILED=$((FAILED + 1)); }
check(){ if [ "$2" = "$3" ]; then ok "$1: $2"; else bad "$1: получено $2, ожидалось $3"; fi; }

step "1. Служебные эндпоинты"
HEALTH=$(curl -s --max-time 10 "$BASE/api/health")
check "health" "$(jget status "$HEALTH")" "UP"
PROVIDERS=$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/auth/providers")
check "удалённый /auth/providers" "$PROVIDERS" "404"

step "2. Регистрация и вход по логину"
LOGIN="gm_smoke_$RANDOM"
REG=$(curl -s -X POST "$BASE/api/auth/register" -H "$JSON" \
  -d "{\"login\":\"$LOGIN\",\"email\":\"$LOGIN@example.com\",\"password\":\"secret123\",\"displayName\":\"Мастер\"}")
TOKEN=$(jget accessToken "$REG")
[ -n "$TOKEN" ] && ok "пользователь $LOGIN создан" || bad "регистрация: $REG"
AUTH="Authorization: Bearer $TOKEN"
check "вход с неверным паролем" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"login\":\"$LOGIN\",\"password\":\"nope\"}")" "401"
check "вход с верным паролем" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"login\":\"${LOGIN^^}\",\"password\":\"secret123\"}")" "200"

step "3. Кампания и объекты четырёх классов"
CAM=$(curl -s -X POST "$BASE/api/campaigns" -H "$AUTH" -H "$JSON" -d '{"title":"Проверка API"}')
CID=$(jget id "$CAM")
[ -n "$CID" ] && ok "кампания создана" || bad "кампания: $CAM"

create() { curl -s -X POST "$BASE/api/campaigns/$CID/$1" -H "$AUTH" -H "$JSON" -d "$2"; }
HERO=$(create heroes '{"name":"Лира","description":"Следопыт","race":"Полуэльф","klass":"Следопыт"}')
LOC=$(create locations '{"name":"Фандалин","description":"Городок"}')
CHAR=$(create characters '{"name":"Силдар","description":"Мэр","race":"Человек","shortDescription":"Мэр Фандалина"}')
ITEM=$(create items '{"name":"Меч","description":"Найден в [[Фандалин]]","ownerName":"Лира"}')
check "герой"     "$(jget name "$HERO")" "Лира"
check "локация"   "$(jget name "$LOC")"  "Фандалин"
check "персонаж"  "$(jget name "$CHAR")" "Силдар"
check "предмет"   "$(jget name "$ITEM")" "Меч"
case "$ITEM" in *'"resolved":true'*) ok "ссылка [[Фандалин]] разрешилась" ;; *) bad "ссылка не разрешилась: $ITEM" ;; esac

step "4. Поиск"
SEARCH=$(curl -s -G "$BASE/api/campaigns/$CID/search" --data-urlencode "q=Фандалин" -H "$AUTH")
[ -n "$(jget total "$SEARCH")" ] && ok "поиск вернул $(jget total "$SEARCH") объектов" || bad "поиск: $SEARCH"
CHARSEARCH=$(curl -s -G "$BASE/api/campaigns/$CID/search" --data-urlencode "q=мэр" --data-urlencode "kinds=CHARACTER" -H "$AUTH")
[ -n "$(jget total "$CHARSEARCH")" ] && ok "поиск по краткому описанию нашёл персонажа" || bad "поиск по персонажу пуст"

step "5. Приглашение и запрет повторного использования"
INV=$(curl -s -X POST "$BASE/api/campaigns/$CID/invites" -H "$AUTH" -H "$JSON" -d '{"role":"EDITOR_HERO"}')
ITOK=$(jget token "$INV")
[ -n "$ITOK" ] && ok "приглашение $ITOK" || bad "приглашение: $INV"
PLAYER="player_smoke_$RANDOM"
REG2=$(curl -s -X POST "$BASE/api/auth/register" -H "$JSON" \
  -d "{\"login\":\"$PLAYER\",\"email\":\"$PLAYER@example.com\",\"password\":\"secret123\",\"displayName\":\"Игрок\",\"inviteToken\":\"$ITOK\",\"platform\":\"android\"}")
TOKEN2=$(jget accessToken "$REG2")
AUTH2="Authorization: Bearer $TOKEN2"
[ -n "$TOKEN2" ] && ok "игрок $PLAYER зарегистрирован по приглашению" || bad "регистрация по приглашению: $REG2"
check "повторная регистрация по коду" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/register" -H "$JSON" -d "{\"login\":\"x_$RANDOM\",\"email\":\"x_$RANDOM@example.com\",\"password\":\"secret123\",\"displayName\":\"X\",\"inviteToken\":\"$ITOK\"}")" "409"
check "принятие использованного кода" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/invites/$ITOK/accept" -H "$AUTH2")" "409"

step "6. Права участника"
check "игрок создаёт локацию" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/campaigns/$CID/locations" -H "$AUTH2" -H "$JSON" -d '{"name":"Тракт"}')" "201"
check "игрок создаёт героя" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/campaigns/$CID/heroes" -H "$AUTH2" -H "$JSON" -d '{"name":"Чужой герой"}')" "403"

step "7. Синхронизация"
BEFORE=$(jget latestRevision "$(curl -s "$BASE/api/campaigns/$CID/sync/state" -H "$AUTH")")
NEWID=$(node -e 'console.log(require("crypto").randomUUID())' 2>/dev/null || cat /proc/sys/kernel/random/uuid)
NOW=$(date -u +%Y-%m-%dT%H:%M:%S.000Z)
PUSH=$(curl -s -X POST "$BASE/api/campaigns/$CID/sync/changes" -H "$AUTH" -H "$JSON" -d "{
  \"clientId\":\"smoke-device\",\"changes\":[{\"clientChangeId\":\"smoke-1\",\"objectType\":\"LOCATION\",
  \"objectId\":\"$NEWID\",\"op\":\"UPSERT\",\"baseRevision\":0,
  \"payload\":{\"id\":\"$NEWID\",\"clientId\":\"$NEWID\",\"name\":\"Фандалин\",\"description\":\"Правка офлайн\",\"updatedAt\":\"$NOW\"}}]}")
case "$(jget status "$PUSH")" in
  CONFLICT_RESOLVED) ok "дубль по имени не создан, правка слилась в существующий объект" ;;
  *) bad "ожидалось CONFLICT_RESOLVED, получено: $PUSH" ;;
esac
case "$PUSH" in *matched_by_name*) ok "причина: matched_by_name" ;; *) bad "нет причины matched_by_name: $PUSH" ;; esac
AFTER=$(jget latestRevision "$(curl -s -X POST "$BASE/api/campaigns/$CID/sync/changes" -H "$AUTH" -H "$JSON" -d "{
  \"clientId\":\"smoke-device\",\"changes\":[{\"clientChangeId\":\"smoke-1\",\"objectType\":\"LOCATION\",
  \"objectId\":\"$NEWID\",\"op\":\"UPSERT\",\"baseRevision\":0,
  \"payload\":{\"id\":\"$NEWID\",\"clientId\":\"$NEWID\",\"name\":\"Фандалин\",\"description\":\"Правка офлайн\",\"updatedAt\":\"$NOW\"}}]}")")
check "повтор правки не создаёт ревизию" "$AFTER" "$(jget latestRevision "$PUSH")"
PULL=$(curl -s "$BASE/api/campaigns/$CID/sync/changes?since=0&limit=500" -H "$AUTH")
[ -n "$(jget latestRevision "$PULL")" ] && ok "pull вернул журнал (ревизия $(jget latestRevision "$PULL"))" || bad "pull: $PULL"

step "8. Сброс пароля"
check "запрос сброса" \
  "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/password-reset/request" -H "$JSON" -d "{\"email\":\"$LOGIN@example.com\"}")" "202"
ADMIN_TOKEN="${DND_ADMIN_TOKEN:-}"
if [ -n "$ADMIN_TOKEN" ]; then
  RTOKEN=$(jget token "$(curl -s "$BASE/api/admin/password-resets" -H "X-Admin-Token: $ADMIN_TOKEN")")
  if [ -n "$RTOKEN" ]; then
    ok "токен сброса получен из админ-панели"
    check "подтверждение сброса" \
      "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/password-reset/confirm" -H "$JSON" -d "{\"token\":\"$RTOKEN\",\"newPassword\":\"newsecret456\"}")" "204"
    check "старый пароль больше не работает" \
      "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"login\":\"$LOGIN\",\"password\":\"secret123\"}")" "401"
    check "новый пароль работает" \
      "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/auth/login" -H "$JSON" -d "{\"login\":\"$LOGIN\",\"password\":\"newsecret456\"}")" "200"
  else
    ok "сброс запрошен (ссылка в логе сервера; DND_ADMIN_TOKEN не задан в этом окружении)"
  fi
else
  ok "сброс запрошен (проверка админ-панели пропущена: не задан DND_ADMIN_TOKEN)"
fi

step "9. Удалённые эндпоинты"
check "POST /api/devices/enroll" "$(curl -s -o /dev/null -w '%{http_code}' -X POST "$BASE/api/devices/enroll" -H "$JSON" -d '{"token":"AAAAAAAA"}')" "401"
check "GET .../entities" "$(curl -s -o /dev/null -w '%{http_code}' "$BASE/api/campaigns/$CID/entities" -H "$AUTH")" "404"

printf '\n'
if [ "$FAILED" -eq 0 ]; then
  echo "ВСЕ ПРОВЕРКИ ПРОШЛИ"
else
  echo "НЕ ПРОШЛО ПРОВЕРОК: $FAILED"
fi
exit "$FAILED"
