#!/usr/bin/env bash
# Восстанавливает экземпляр из копии, сделанной bin/backup.sh.
#
#   sudo bash bin/restore.sh test /var/backups/dnd-memory/test/2026-11-13/db.sql.gz
#   sudo bash bin/restore.sh test .../db.sql.gz .../files.tar.gz   # и картинки
#   sudo bash bin/restore.sh                                      # список свежих копий
#
# ВНИМАНИЕ: схема базы экземпляра перезаписывается целиком (сначала DROP SCHEMA,
# затем дамп), отмены нет. Приложение на время восстановления должно быть
# остановлено — иначе оно будет писать в базу параллельно:
#
#   ./bin/dnd.sh test down
#   sudo bash bin/restore.sh test <дамп>
#   ./bin/dnd.sh test up
#
# База при этом нужна запущенная: скрипт поднимает её сам (./bin/dnd.sh down
# гасит и базу тоже). Дамп накладывается одной транзакцией: при ошибке в
# середине схема остаётся прежней, полусостояния не будет.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BK=/var/backups/dnd-memory
STAND="${1:-}"
DUMP="${2:-}"
FILES="${3:-}"

ok()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
die() { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

list_copies() {
  echo "Свежие копии в $BK:"
  for i in prod test; do
    for d in $(ls -1dt "$BK/$i"/*/ 2>/dev/null | head -3); do
      printf '  %-5s %s' "$i" "$d"
      [ -f "${d}db.sql.gz" ] && printf ' база %s' "$(du -h "${d}db.sql.gz" | cut -f1)"
      [ -e "${d}files.tar.gz" ] && printf '  файлы %s' "$(du -hL "${d}files.tar.gz" | cut -f1)"
      echo
    done
  done
}

case "$STAND" in
  prod|test) ;;
  *) list_copies; echo; echo "Использование: $0 <prod|test> <db.sql.gz> [files.tar.gz]"; exit 1 ;;
esac
[ -f "$DUMP" ] || die "нет дампа: $DUMP"
[ -f "$ROOT/$STAND/.env" ] || die "нет $ROOT/$STAND/.env — это не папка комплекта"

if docker ps --format '{{.Names}}' | grep -q "^dnd-$STAND-app\$"; then
  die "приложение запущено — сначала: $ROOT/bin/dnd.sh $STAND down"
fi

compose() { docker compose -p "dnd-$STAND" --env-file "$ROOT/$STAND/.env" -f "$ROOT/$STAND/docker-compose.yml" "$@"; }

if ! docker ps --format '{{.Names}}' | grep -q "^dnd-$STAND-db\$"; then
  echo "==> поднимаю базу $STAND"
  compose up -d db >/dev/null 2>&1 || die "не удалось поднять базу: $ROOT/bin/dnd.sh $STAND up и повторить"
  for _ in $(seq 1 20); do
    docker exec "dnd-$STAND-db" pg_isready -U dnd -d dnd >/dev/null 2>&1 && break
    sleep 2
  done
fi
docker exec "dnd-$STAND-db" pg_isready -U dnd -d dnd >/dev/null 2>&1 || die "база не принимает соединения"

if [ -t 0 ]; then
  printf 'Перезаписать базу %s дамп %s? [yes/N] ' "$STAND" "$(basename "$DUMP")"
  read -r ANSWER
  [ "$ANSWER" = "yes" ] || die "отменено"
fi

echo "==> $STAND: схема будет перезаписана дампом $(basename "$DUMP")"
# DROP и дамп — одной транзакцией (-1): оборвался дамп откатится вместе с DROP,
# полусостоявшейся схемы не бывает. Кавычки вокруг public не для красоты: без них
# объекты дампа могли бы уйти в другую схему поиска.
{
  printf 'DROP SCHEMA IF EXISTS "public" CASCADE;\n'
  printf 'CREATE SCHEMA "public";\n'
  gunzip -c "$DUMP"
} | docker exec -i "dnd-$STAND-db" psql -U dnd -d dnd -1 -v ON_ERROR_STOP=1 -q ||
  die "psql завершился с ошибкой — схема не изменена, транзакция откатилась"

ROWS=$(docker exec "dnd-$STAND-db" psql -U dnd -d dnd -Atc 'select count(*) from users' 2>/dev/null | tail -1)
ok "база восстановлена (пользователей: ${ROWS:-не посчитать})"

if [ -n "$FILES" ] && [ -f "$FILES" ]; then
  echo "==> $STAND: распаковка файлов из $(basename "$FILES")"
  if [ -d "$ROOT/$STAND/data/files" ]; then
    mv "$ROOT/$STAND/data/files" "$ROOT/$STAND/data/files.before-restore"
    ok "прежние файлы перенесены в data/files.before-restore"
  fi
  tar xzf "$FILES" -C "$ROOT/$STAND/data"
  ok "файлы распакованы"
fi

echo
echo "готово. запуск: $ROOT/bin/dnd.sh $STAND up"
