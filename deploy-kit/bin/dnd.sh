#!/usr/bin/env bash
# Управление экземпляром DnD Memory.
#   ./bin/dnd.sh prod up|down|restart|update|logs|status|health
#   ./bin/dnd.sh test ...
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
INSTANCE="${1:-}"
CMD="${2:-up}"

case "$INSTANCE" in
  prod|test) ;;
  *) echo "Использование: $0 <prod|test> <up|down|restart|update|logs|status|health>" >&2; exit 1 ;;
esac

DIR="$ROOT/$INSTANCE"
ENV_FILE="$DIR/.env"
COMPOSE_FILE="$DIR/docker-compose.yml"

command -v docker >/dev/null || { echo "Docker не установлен: sudo bash $ROOT/bin/install-docker.sh" >&2; exit 1; }
[ -f "$ENV_FILE" ] || { echo "Нет файла $ENV_FILE" >&2; echo "Скопируйте: cp $DIR/.env.example $ENV_FILE && nano $ENV_FILE" >&2; exit 1; }
[ -f "$DIR/app.jar" ] || { echo "Нет $DIR/app.jar — положите сюда собранный jar" >&2; exit 1; }
[ -f "$ROOT/web-$INSTANCE/index.html" ] || echo "Внимание: нет $ROOT/web-$INSTANCE/index.html — веб-версия не откроется (в комплекте папки web-prod и web-test уже есть)" >&2
mkdir -p "$DIR/data/files"

compose() { docker compose -p "dnd-$INSTANCE" --env-file "$ENV_FILE" -f "$COMPOSE_FILE" "$@"; }

case "$CMD" in
  up)      compose up -d ;;
  down)    compose down ;;
  restart) compose up -d --force-recreate ;;
  # после замены app.jar: контейнер пересоздаётся и подхватывает новый файл
  update)  compose up -d --force-recreate app ;;
  logs)    compose logs -f --tail=100 ;;
  status)  compose ps ;;
  health)
    PORT="$(grep -E '^APP_PORT=' "$ENV_FILE" | cut -d= -f2)"
    curl -s --max-time 5 "http://127.0.0.1:${PORT:-8090}/api/health" || echo "не отвечает"
    echo ;;
  *) echo "Команды: up | down | restart | update | logs | status | health" >&2; exit 1 ;;
esac
