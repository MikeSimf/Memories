#!/usr/bin/env bash
# Что происходит с экземплярами: статусы, последние строки логов, место и память.
#   ./bin/diagnose.sh            — оба экземпляра
#   ./bin/diagnose.sh prod       — только рабочий
set -uo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ONLY="${1:-both}"

TARGETS="prod test"
[ "$ONLY" = "both" ] || TARGETS="$ONLY"

echo "== Docker"
docker --version 2>/dev/null || echo "docker не найден"

echo
echo "== Контейнеры"
docker ps -a --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'NAMES|dnd-' || echo "нет контейнеров dnd-*"

echo
echo "== Место и память"
df -h / /var/lib/docker 2>/dev/null | tail -2
free -m 2>/dev/null | head -2

for inst in $TARGETS; do
  echo
  echo "== $inst: логи приложения (последние 40 строк)"
  docker logs --tail 40 "dnd-$inst-app" 2>&1 | tail -40 || echo "контейнер dnd-$inst-app не найден"

  echo
  echo "== $inst: ошибки запуска"
  docker logs "dnd-$inst-app" 2>&1 | grep -iE 'error|exception|failed|flyway|checksum' | tail -15 || true

  echo
  echo "== $inst: база"
  docker logs --tail 10 "dnd-$inst-db" 2>&1 | tail -10 || true

  PORT="$(grep -E '^APP_PORT=' "$ROOT/$inst/.env" 2>/dev/null | cut -d= -f2)"
  echo
  echo "== $inst: ответ API на 127.0.0.1:${PORT}"
  curl -s --max-time 5 "http://127.0.0.1:${PORT}/api/health" || echo "не отвечает"
  echo
done
