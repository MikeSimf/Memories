#!/usr/bin/env bash
# Поднимает общий шлюз (nginx на 80/443) для всех проектов сервера.
# Запускать один раз после установки Docker и после каждого обновления конфигурации шлюза.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
command -v docker >/dev/null 2>&1 || { echo "Docker не установлен: scripts/install-docker.sh" >&2; exit 1; }

if ! docker network inspect edge >/dev/null 2>&1; then
  docker network create edge >/dev/null
  echo "==> Создана общая сеть edge"
fi

mkdir -p "$ROOT/docker/ingress/conf.d" "$ROOT/docker/ingress/certbot-www"
cd "$ROOT"
docker compose -p ingress -f docker/ingress/compose.yml up -d
docker compose -p ingress -f docker/ingress/compose.yml ps
echo "==> Шлюз поднят. Домены: ./scripts/ingress-domain.sh list"
