#!/usr/bin/env bash
# Выкладывает новую версию приложения: apk + версия в конфиге + перезапуск.
#
#   ./bin/deploy-app.sh /tmp/dnd-memory.apk 10 1.0.9
#   ./bin/deploy-app.sh /tmp/dnd-memory.apk 10 1.0.9 test     (только тестовый)
#
# Что делает:
#   1. копирует apk в web-prod/ и web-test/ под именем dnd-memory.apk;
#   2. прописывает latest-version-code / latest-version-name в application.yml
#      обоих экземпляров (min-version-code не трогает);
#   3. перезапускает контейнеры приложения: без перезапуска сервер продолжает
#      отдавать старые значения из уже прочитанного конфига.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APK="${1:-}"
CODE="${2:-}"
NAME="${3:-}"
ONLY="${4:-both}"

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

command -v docker >/dev/null || die "Docker не установлен: sudo bash $ROOT/bin/install-docker.sh"

[ -n "$APK" ] && [ -n "$CODE" ] && [ -n "$NAME" ] || \
  die "Использование: $0 <путь к apk> <versionCode> <versionName> [prod|test|both]"
[ -f "$APK" ] || die "Нет файла: $APK"
case "$CODE" in ''|*[!0-9]*) die "versionCode должен быть числом: $CODE" ;; esac
case "$ONLY" in prod|test|both) ;; *) die "Третий аргумент: prod, test или both" ;; esac

TARGETS="prod test"
[ "$ONLY" = "both" ] || TARGETS="$ONLY"

for inst in $TARGETS; do
  [ -d "$ROOT/web-$inst" ] || die "Нет папки $ROOT/web-$inst — распакуйте комплект"
  cp "$APK" "$ROOT/web-$inst/dnd-memory.apk"
  # nginx работает не под root: файл должен быть читаемым для всех
  chmod a+r "$ROOT/web-$inst/dnd-memory.apk"
  ok "apk: web-$inst/dnd-memory.apk ($(du -h "$APK" | cut -f1))"

  CFG="$ROOT/$inst/application.yml"
  [ -f "$CFG" ] || die "Нет конфигурации $CFG"
  sed -i.bak -E \
    -e "s|^(    latest-version-code:).*|\1 $CODE|" \
    -e "s|^(    latest-version-name:).*|\1 $NAME|" \
    "$CFG" && rm -f "$CFG.bak"
  ok "$inst/application.yml: $CODE / $NAME"
  grep -E '^    latest-version-(code|name):' "$CFG" | sed 's/^/      /'
done

for inst in $TARGETS; do
  [ -f "$ROOT/$inst/.env" ] || die "Нет $ROOT/$inst/.env"
  bash "$ROOT/bin/dnd.sh" "$inst" restart >/dev/null
  ok "$inst перезапущен (конфиг читается при старте)"
done

echo
echo "==> Проверка (значения появятся через несколько секунд):"
for inst in $TARGETS; do
  PORT="$(grep -E '^APP_PORT=' "$ROOT/$inst/.env" | cut -d= -f2)"
  sleep 2
  printf '  %s: ' "$inst"
  curl -s --max-time 5 "http://127.0.0.1:${PORT}/api/app/latest" || echo "не отвечает"
  echo
done
