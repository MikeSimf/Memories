#!/usr/bin/env bash
# Пересборка и запуск контура DnD Memory.
#   ./scripts/deploy.sh test    — тестовый контур (порт 8081)
#   ./scripts/deploy.sh prod    — рабочий контур (80/443, TLS)
set -euo pipefail

CONTOUR="${1:-}"
if [[ "$CONTOUR" != "test" && "$CONTOUR" != "prod" ]]; then
  echo "Использование: $0 <test|prod>" >&2
  exit 1
fi

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT/docker/env.$CONTOUR"
COMPOSE_FILE="$ROOT/docker/compose.$CONTOUR.yml"
PROJECT="dnd-$CONTOUR"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "Нет файла $ENV_FILE." >&2
  echo "Скопируйте docker/env.$CONTOUR.example в docker/env.$CONTOUR и заполните значения." >&2
  exit 1
fi

command -v docker >/dev/null 2>&1 || { echo "Docker не установлен. Сначала запустите scripts/install-docker.sh" >&2; exit 1; }

# Общая сеть нужна, чтобы шлюз (docker/ingress) видел контейнеры этого контура
if ! docker network inspect edge >/dev/null 2>&1; then
  docker network create edge >/dev/null
  echo "==> Создана общая сеть edge"
fi

cd "$ROOT"
echo "==> Контур: $CONTOUR (проект $PROJECT)"
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" build
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" up -d
docker compose -p "$PROJECT" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" ps

if [[ "$CONTOUR" == "test" ]]; then
  PORT="$(grep -E '^TEST_PORT=' "$ENV_FILE" | cut -d= -f2 || true)"
  echo "==> Готово. Тестовый контур: http://<адрес сервера>:${PORT:-8081}"
else
  DOMAIN="$(grep -E '^DOMAIN=' "$ENV_FILE" | cut -d= -f2 || true)"
  echo "==> Готово. Рабочий контур: https://$DOMAIN"
fi
echo "==> Логи: docker compose -p $PROJECT --env-file $ENV_FILE -f $COMPOSE_FILE logs -f server"
