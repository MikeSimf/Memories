#!/usr/bin/env bash
# Полный прогон тестов сервера с настоящим PostgreSQL (одноразовый контейнер).
# Требует Docker; на машине разработчика это самый честный способ проверить Flyway и JPA.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
NET="dnd-itest-$$"
export DND_DB_URL="jdbc:postgresql://127.0.0.1:55432/dnd?stringtype=unspecified"
export DND_DB_USER=dnd
export DND_DB_PASSWORD=dnd

cleanup() { docker rm -f "$NET-db" >/dev/null 2>&1 || true; }
trap cleanup EXIT

docker run -d --name "$NET-db" -p 127.0.0.1:55432:5432 \
  -e POSTGRES_DB=dnd -e POSTGRES_USER=dnd -e POSTGRES_PASSWORD=dnd postgres:15-alpine >/dev/null

echo "==> Ждём готовности PostgreSQL"
for _ in $(seq 1 30); do
  if docker exec "$NET-db" pg_isready -U dnd -d dnd >/dev/null 2>&1; then break; fi
  sleep 1
done

cd "$ROOT/server"
mvn -B test "$@"
