#!/usr/bin/env bash
# Одна строка в /var/log/dnd-monitor.log: живы ли экземпляры и не вернулись ли
# конкретные болячки, которые уже чинили. Запускать по часам из cron:
#
#   47 * * * * /opt/dnd-memory/bin/monitor.sh
#
# Считает ровно то, что ломало прод:
#   заголовки — «Content-Disposition ... has been removed»: кириллица в filename=
#              (файл скачивался с именем из URL);
#   дата      — DateTimeParseException на пустом sessionDate: один пересказ ронял
#              весь пуш, правка повторялась при каждом соединении;
#   необраб   — необработанные исключения (GlobalExceptionHandler);
#   пуш       — сколько запросов синхронизации реально прошло за час: без них
#              нули выше значат лишь то, что трафика не было.
set -u
OUT=/var/log/dnd-monitor.log
{
for STAND in prod test; do
  APP="dnd-$STAND-app"
  WEB="dnd-$STAND-web"
  PORT=8080; [ "$STAND" = "test" ] && PORT=8081

  HEADERS=$(docker logs --since 1h "$APP" 2>&1 | grep -c 'Content-Disposition')
  DATE=$(docker logs --since 1h "$APP" 2>&1 | grep -c 'DateTimeParseException')
  OTHER=$(docker logs --since 1h "$APP" 2>&1 | grep -c 'GlobalExceptionHandler')
  PUSH=$(docker logs --since 1h "$WEB" 2>&1 | grep -c 'sync/push')
  HEALTH=$(curl -fsS --max-time 5 "http://127.0.0.1:$PORT/api/health" >/dev/null 2>&1 && echo up || echo DOWN)

  BAD=""
  [ "$HEALTH" = DOWN ] && BAD="$BAD стенд-не-отвечает"
  [ "$HEADERS" -gt 0 ] && BAD="$BAD заголовки"
  [ "$DATE" -gt 0 ] && BAD="$BAD дата-пуша"
  [ "$OTHER" -gt 0 ] && BAD="$BAD необработанные"

  printf '%s %-5s health=%-4s пуш=%-4s заголовки=%-4s дата=%-3s необраб=%-3s%s%s\n' \
    "$(date -Is)" "$STAND" "$HEALTH" "$PUSH" "$HEADERS" "$DATE" "$OTHER" \
    "${BAD:+  ВЕРНУЛИСЬ:}" "$BAD"
done
} >> "$OUT" 2>&1

# Лог держим коротким: последние 200 строк — это неделя часовых замеров.
if [ "$(wc -l < "$OUT")" -gt 400 ]; then
  tail -200 "$OUT" > "$OUT.tmp" && mv "$OUT.tmp" "$OUT"
fi
exit 0
