#!/usr/bin/env bash
# Поднимает те экземпляры, которые настроены (есть .env и app.jar).
set -u
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
rc=0
for inst in prod test; do
  if [ -f "$ROOT/$inst/.env" ] && [ -f "$ROOT/$inst/app.jar" ]; then
    echo "==> $inst"
    bash "$ROOT/bin/dnd.sh" "$inst" up || rc=1
  else
    echo "==> $inst пропущен (нет .env или app.jar)"
  fi
done
exit $rc
