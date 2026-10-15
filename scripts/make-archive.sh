#!/usr/bin/env bash
# Собирает архив проекта для передачи (без node_modules, target и прочей сборки).
#   ./scripts/make-archive.sh [куда]
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
TARGET_DIR="${1:-$ROOT/../}"
STAMP="$(date +%Y-%m-%d)"
NAME="dnd-memory-$STAMP.tar.gz"

cd "$ROOT/.."
tar czf "$TARGET_DIR/$NAME" \
  --exclude='dnd-memory/.git' \
  --exclude='dnd-memory/web/node_modules' \
  --exclude='dnd-memory/web/dist' \
  --exclude='dnd-memory/server/target' \
  --exclude='dnd-memory/android/.gradle' \
  --exclude='dnd-memory/android/app/build' \
  --exclude='dnd-memory/backups' \
  --exclude='dnd-memory/docker/env.test' \
  --exclude='dnd-memory/docker/env.prod' \
  dnd-memory

echo "Архив: $TARGET_DIR/$NAME ($(du -h "$TARGET_DIR/$NAME" | cut -f1))"
