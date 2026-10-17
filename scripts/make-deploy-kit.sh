#!/usr/bin/env bash
# Пересобирает архив комплекта развёртывания в release/.
# Запускать из корня репозитория:  bash scripts/make-deploy-kit.sh
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/deploy-kit"

OUT="$ROOT/release/dnd-memory-deploy-kit.tar.gz"
mkdir -p "$ROOT/release"

# В архив кладём ровно то, что нужно на сервере: prod/, test/, bin/,
# собранные веб-версии web-prod/ и web-test/ и README.
# Секреты (.env), jar (app.jar) и данные (data/) сюда не попадают — они gitignore.
tar czf "$OUT" README.md prod test bin web-prod web-test

echo "готово: $OUT ($(du -h "$OUT" | cut -f1))"
echo
echo "Содержимое (без файлов в assets/):"
tar tzf "$OUT" | grep -v '/assets/' | sed 's/^/  /'
