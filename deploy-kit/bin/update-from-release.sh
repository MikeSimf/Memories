#!/usr/bin/env bash
# Обновляет оба экземпляра до сборки из релиза: скачивает комплект, jar и apk,
# распаковывает статику и перезапускает контейнеры.
#
#   sudo bash bin/update-from-release.sh
#   sudo bash bin/update-from-release.sh 14 1.0.13
#
# Секреты (.env) и база не трогаются: в архиве комплекта их нет.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE="${DND_RELEASE_BASE:-https://github.com/MikeSimf/Memories/releases/download/v1.0.0}"
CODE="${1:-}"
NAME="${2:-}"
TMP="${TMPDIR:-/tmp}/dnd-update-$$"

ok()  { printf '  \033[32m✓\033[0m %s\n' "$1"; }
die() { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

command -v curl >/dev/null || die "Нужен curl"
command -v tar >/dev/null || die "Нужен tar"
[ -f "$ROOT/prod/.env" ] && [ -f "$ROOT/test/.env" ] || \
  die "Нет $ROOT/prod/.env или $ROOT/test/.env — это не папка комплекта"

mkdir -p "$TMP"
trap 'rm -rf "$TMP"' EXIT

echo "==> Скачиваю сборку"
curl -fL --retry 3 --retry-delay 2 -o "$TMP/kit.tar.gz" "$BASE/dnd-memory-deploy-kit.tar.gz"
curl -fL --retry 3 --retry-delay 2 -o "$TMP/app.jar" "$BASE/dnd-memory-server.jar"
curl -fL --retry 3 --retry-delay 2 -o "$TMP/app.apk" "$BASE/dnd-memory-test.apk"
ok "скачано"

echo "==> Распаковываю статику и скрипты в $ROOT"
tar xzf "$TMP/kit.tar.gz" -C "$ROOT"
chmod +x "$ROOT"/bin/*.sh "$ROOT"/prod/*.sh "$ROOT"/test/*.sh 2>/dev/null || true
ok "комплект обновлён"

for inst in prod test; do
  cp "$TMP/app.jar" "$ROOT/$inst/app.jar"
  ok "$inst/app.jar заменён"
done

# Версию берём из свежего конфига комплекта, чтобы не расходиться с apk.
[ -n "$CODE" ] || CODE=$(sed -n 's/^    latest-version-code: //p' "$ROOT/prod/application.yml" | head -1)
[ -n "$NAME" ] || NAME=$(sed -n 's/^    latest-version-name: //p' "$ROOT/prod/application.yml" | head -1)
[ -n "$CODE" ] && [ -n "$NAME" ] || die "Не нашёл версию в $ROOT/prod/application.yml"

# Перезапуск подхватывает новый jar и новые version-code/name из конфига.
bash "$ROOT/bin/deploy-app.sh" "$TMP/app.apk" "$CODE" "$NAME" both

echo
echo "==> Проверка"
for url in "https://7f6a7dc97a7c.vps.myjino.ru/api/health" "http://81.177.139.30:8091/api/health"; do
  printf '  %s — ' "$url"
  curl -fsS --max-time 10 "$url" || printf 'не ответил'
  echo
done
