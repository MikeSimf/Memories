#!/usr/bin/env bash
# Восстановление базы из резервной копии.
#   ./scripts/restore.sh test backups/dnd-test-2025-01-01_120000.sql.gz
set -euo pipefail

CONTOUR="${1:-}"
FILE="${2:-}"
if [[ "$CONTOUR" != "test" && "$CONTOUR" != "prod" ]] || [[ ! -f "${FILE:-}" ]]; then
  echo "Использование: $0 <test|prod> <файл.sql.gz>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/docker/env.$CONTOUR"
PROJECT="dnd-$CONTOUR"
COMPOSE=(docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$ROOT/docker/compose.$CONTOUR.yml")

read -r -p "Это перезапишет данные контура $CONTOUR. Продолжить? [y/N] " answer
[[ "$answer" == "y" || "$answer" == "Y" ]] || exit 1

"${COMPOSE[@]}" stop server
gunzip -c "$FILE" | "${COMPOSE[@]}" exec -T db psql -U dnd -d dnd
"${COMPOSE[@]}" start server
echo "Восстановление завершено."
