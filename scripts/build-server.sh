#!/usr/bin/env bash
# Сборка сервера с проектными настройками Maven.
#
# Зачем: если в ~/.m2/settings.xml прописано рабочее зеркало, оно перехватывает все
# репозитории и проект может не собраться. Этот скрипт подставляет настройки проекта
# (Maven Central) и не трогает ваши рабочие настройки для остальных проектов.
#
#   ./scripts/build-server.sh                 # mvn package
#   ./scripts/build-server.sh clean package   # любые цели Maven
#   ./scripts/build-server.sh test            # только тесты
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

exec mvn -s "$ROOT/server/.mvn/settings.xml" -f "$ROOT/server/pom.xml" "$@"
