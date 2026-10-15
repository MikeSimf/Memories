#!/usr/bin/env bash
# Резервная копия базы контура в backups/. Запускать на сервере из корня репозитория.
#   ./scripts/backup.sh test
set -euo pipefail

CONTOUR="${1:-}"
if [[ "$CONTOUR" != "test" && "$CONTOUR" != "prod" ]]; then
  echo "Использование: $0 <test|prod>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/docker/env.$CONTOUR"
PROJECT="dnd-$CONTOUR"
mkdir -p "$ROOT/backups"
STAMP="$(date +%Y-%m-%d_%H%M%S)"
TARGET="$ROOT/backups/dnd-$CONTOUR-$STAMP.sql.gz"

docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$ROOT/docker/compose.$CONTOUR.yml" \
  exec -T db pg_dump -U dnd -d dnd | gzip > "$TARGET"

echo "Резервная копия: $TARGET ($(du -h "$TARGET" | cut -f1))"
echo "Храните копии вне сервера — том Docker переживёт перезапуск, но не потерю диска."
