#!/usr/bin/env bash
# Выкладывает собранную веб-версию на нужный экземпляр.
#   ./bin/deploy-web.sh test /путь/к/dist              (каталог сборки)
#   ./bin/deploy-web.sh prod /путь/к/web-dist.tar.gz   (архив сборки)
#
# Контейнер web раздаёт статику из папки web-<экземпляр> через bind-mount,
# поэтому перезапускать его не нужно: nginx читает файлы с диска на каждый
# запрос. Старые файлы удаляются — иначе останутся ассеты прошлой сборки.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTANCE="${1:-}"
SRC="${2:-}"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

case "$INSTANCE" in
  prod|test) ;;
  *) die "Использование: $0 <prod|test> <каталог dist | архив .tar.gz>" ;;
esac
[ -n "$SRC" ] || die "Укажите, что выкладывать: каталог сборки или .tar.gz"
[ -e "$SRC" ] || die "Не найдено: $SRC"

DIR="$ROOT/web-$INSTANCE"
[ -d "$DIR" ] || die "Нет папки $DIR — сначала распакуйте комплект в /opt/dnd-memory"

STAGE="$(mktemp -d)"
trap 'rm -rf "$STAGE"' EXIT

if [ -d "$SRC" ]; then
  cp -a "$SRC"/. "$STAGE"/
elif [ -f "$SRC" ]; then
  tar xzf "$SRC" -C "$STAGE"
else
  die "Не понимаю, что это: $SRC"
fi

# Сборка Vite всегда содержит index.html и папку assets
[ -f "$STAGE/index.html" ] || die "В $SRC нет index.html — это не собранная веб-версия (нужен результат npm run build)"

ok "сборка: index.html $(du -sh "$STAGE" | cut -f1)"

# Кладём на место: сначала чистим, потом копируем (папка смонтирована в контейнер,
# поэтому заменять сам каталог нельзя — только его содержимое)
find "$DIR" -mindepth 1 -delete
cp -a "$STAGE"/. "$DIR"/
# Права на чтение: nginx работает не под root, файл с режимом 600 даёт 403.
chmod -R a+rX "$DIR"
ok "выложено в $DIR"

PORT="$(grep -E '^APP_PORT=' "$ROOT/$INSTANCE/.env" 2>/dev/null | cut -d= -f2 || true)"
PORT="${PORT:-}"
if [ -n "$PORT" ]; then
  sleep 1
  CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://127.0.0.1:$PORT/" || true)
  if [ "$CODE" = "200" ]; then
    ok "http://127.0.0.1:$PORT/ отвечает 200"
  else
    printf '  \033[33m!\033[0m http://127.0.0.1:%s/ -> %s (контейнер запущен? ./%s/start.sh)\n' "$PORT" "${CODE:-нет ответа}" "$INSTANCE" >&2
  fi
fi

cat <<HINT

  Браузер может держать старую версию из кэша service worker:
  обновите страницу с Ctrl+Shift+R (или «Обновить с очисткой кэша»).
HINT
