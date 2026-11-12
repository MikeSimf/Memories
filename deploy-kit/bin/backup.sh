#!/usr/bin/env bash
# Резервные копии обоих экземпляров: дамп базы и загруженные картинки.
#
#   sudo bash bin/backup.sh            # оба экземпляра
#   sudo bash bin/backup.sh prod       # только рабочий
#
# Хранит в /var/backups/dnd-memory/<экземпляр>/<дата>/, держит KEEP дней.
# Пароль базы не покидает сервер: pg_dump запускается внутри контейнера.
#
# Картинки полностью копируются раз в сутки (в 03 часа), в остальное время
# на свежий снимок ставится символльная ссылка — иначе часовой cron съест диск.
#
# Восстановление — bin/restore.sh. Запуск по cron (см. README, шаг «копии»):
#   17 * * * * /opt/dnd-memory/bin/backup.sh >> /var/backups/dnd-memory/cron.log 2>&1
#
# Для раскладки docker/compose.<контур>.yml есть отдельный scripts/backup.sh.
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BK=/var/backups/dnd-memory
KEEP=14
STAND="${1:-both}"
DAY=$(date +%F)
HOUR=$(date +%H)
mkdir -p "$BK"

[ "$HOUR" = "03" ] && MODE=full || MODE=incr

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1" >&2; }

docker ps --format '{{.Names}}' | grep -q '^dnd-' || \
  warn "контейнеры dnd-* не запущены — база рабочего экземпляра не будет скопирована"

for inst in prod test; do
  [ "$STAND" = "both" ] || [ "$STAND" = "$inst" ] || continue
  DIR="$BK/$inst/$DAY"
  mkdir -p "$DIR"

  if docker exec "dnd-$inst-db" pg_dump -U dnd -d dnd 2>/dev/null | gzip > "$DIR/db.sql.gz"; then
    if [ -s "$DIR/db.sql.gz" ] && gzip -t "$DIR/db.sql.gz"; then
      ok "$inst: база $(du -h "$DIR/db.sql.gz" | cut -f1)"
    else
      rm -f "$DIR/db.sql.gz"
      warn "$inst: дамп пустой или битый — копия НЕ записана"
      continue
    fi
  else
    warn "$inst: pg_dump завершился с ошибкой — копия НЕ записана"
    continue
  fi

  FILES="$ROOT/$inst/data/files"
  if [ -d "$FILES" ]; then
    if [ -e "$DIR/files.tar.gz" ]; then
      # Снимок за сегодня уже есть (например, полный в 03 часа) — не трогаем:
      # иначе следующая часовая копия заменила бы готовый архив ссылкой.
      ok "$inst: снимок файлов за $DAY уже есть ($(du -h "$DIR/files.tar.gz" | cut -f1))"
    elif [ "$MODE" = "full" ]; then
      if tar czf "$DIR/files.tar.gz" -C "$ROOT/$inst/data" files 2>/dev/null; then
        ok "$inst: файлы $(du -h "$DIR/files.tar.gz" | cut -f1)"
      else
        rm -f "$DIR/files.tar.gz"
        warn "$inst: архив файлов не собрался"
      fi
    else
      # Ссылка на самый свежий полный снимок ДРУГОГО дня: на снимок этого же дня
      # ссылка не нужна (это тот же файл) и ln на него отказывается работать.
      LAST=$(find "$BK/$inst" -mindepth 2 -maxdepth 2 -name files.tar.gz ! -path "$DIR/*" -printf '%T@ %p\n' 2>/dev/null \
             | sort -rn | head -1 | cut -d' ' -f2- || true)
      if [ -n "$LAST" ]; then
        if ln -sLf "$LAST" "$DIR/files.tar.gz"; then
          ok "$inst: файлы — ссылка на $(basename "$(dirname "$LAST")")"
        else
          warn "$inst: не удалось поставить ссылку на снимок файлов"
        fi
      else
        if tar czf "$DIR/files.tar.gz" -C "$ROOT/$inst/data" files 2>/dev/null; then
          ok "$inst: файлы (первый снимок) $(du -h "$DIR/files.tar.gz" | cut -f1)"
        else
          rm -f "$DIR/files.tar.gz"
          warn "$inst: архив файлов не собрался"
        fi
      fi
    fi
  fi
done

# Ротация: старше KEEP дней вычищаем целиком.
find "$BK" -mindepth 1 -maxdepth 1 -type d -mtime +"$KEEP" -exec rm -rf {} + 2>/dev/null || true

# Журналируем объём и самое свежее число копии по каждому экземпляру,
# чтобы по логу было видно: копии реально появляются и не кончались неделю назад.
LAST_PROD=$(ls -1 "$BK/prod" 2>/dev/null | grep -E '^[0-9]{4}-' | sort | tail -1 || true)
LAST_TEST=$(ls -1 "$BK/test" 2>/dev/null | grep -E '^[0-9]{4}-' | sort | tail -1 || true)
echo "$(date -Is) mode=$MODE used=$(du -sh "$BK" 2>/dev/null | cut -f1) prod=$LAST_PROD test=$LAST_TEST" >> "$BK/backup.log"
