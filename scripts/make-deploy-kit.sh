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
# Проверка YAML перед упаковкой: незакавыченное значение с «: » внутри
# ломает Spring («mapping values are not allowed here») и контейнер уходит
# в перезапуск. Ловим это до того, как архив уедет на сервер.
node - <<'NODE'
const fs = require('fs');
const files = [
  'prod/application.yml', 'test/application.yml',
  'prod/docker-compose.yml', 'test/docker-compose.yml',
];
let failed = false;
for (const file of files) {
  const lines = fs.readFileSync(file, 'utf8').split('\n');
  lines.forEach((line, index) => {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith('#') || trimmed.startsWith('-')) return;
    const match = /^[^:#]+:\s+(.+)$/.exec(trimmed);
    if (!match) return;
    const value = match[1].trim();
    if (value.startsWith('"') || value.startsWith("'") || value.startsWith('|') || value.startsWith('>')) return;
    if (value.includes(': ') || value.endsWith(':')) {
      console.error('  ✗ ' + file + ':' + (index + 1) + ' — значение с двоеточием нужно взять в кавычки');
      console.error('     ' + trimmed);
      failed = true;
    }
  });
}
if (failed) process.exit(1);
NODE
if [ $? -ne 0 ]; then
  echo "архив не собран: исправьте YAML" >&2
  exit 1
fi

tar czf "$OUT" README.md nginx-host.conf prod test bin web-prod web-test

echo "готово: $OUT ($(du -h "$OUT" | cut -f1))"
echo
echo "Содержимое (без файлов в assets/):"
tar tzf "$OUT" | grep -v '/assets/' | sed 's/^/  /'
