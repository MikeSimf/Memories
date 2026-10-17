#!/usr/bin/env bash
# Публикует приложение за уже установленным nginx: добавляет виртуальный хост с HTTPS.
#
#   sudo bash scripts/https-behind-nginx.sh 7f6a7dc97a7c.vps.myjino.ru
#   sudo bash scripts/https-behind-nginx.sh example.com 8090 /путь/fullchain.pem /путь/privkey.pem
#
# Пути к сертификату по умолчанию берутся из действующей конфигурации nginx
# (обычно это wildcard-сертификат хостера). Скрипт ничего не удаляет: если `nginx -t`
# после правки не проходит, добавленный файл убирается и конфигурация не перезагружается.
set -euo pipefail

DOMAIN="${1:?Укажите домен, например 7f6a7dc97a7c.vps.myjino.ru}"
PORT="${2:-8090}"
CERT_ARG="${3:-}"
KEY_ARG="${4:-}"

CONF=/etc/nginx/sites-available/dnd-memory.conf
LINK=/etc/nginx/sites-enabled/dnd-memory.conf

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

[ "$(id -u)" = "0" ] || die "нужны права root: sudo bash $0 $DOMAIN"
command -v nginx >/dev/null || die "nginx не установлен"

echo "==> Домен: $DOMAIN, приложение на 127.0.0.1:$PORT"

# 1. приложение должно отвечать локально
if curl -sf --max-time 5 "http://127.0.0.1:$PORT/api/health" >/dev/null; then
  ok "приложение отвечает на 127.0.0.1:$PORT"
else
  warn "на 127.0.0.1:$PORT ничего не отвечает — виртуальный хост будет отдавать 502"
  warn "сначала поднимите контур: docker compose -p dnd-prod ... up -d"
fi

# 2. сертификат: из аргументов или из действующей конфигурации
if [ -n "$CERT_ARG" ] && [ -n "$KEY_ARG" ]; then
  CERT="$CERT_ARG"; KEY="$KEY_ARG"
else
  CERT=$(nginx -T 2>/dev/null | sed -n 's/^[[:space:]]*ssl_certificate[[:space:]]\+\([^;]*\);.*/\1/p' | head -1)
  KEY=$(nginx -T 2>/dev/null | sed -n 's/^[[:space:]]*ssl_certificate_key[[:space:]]\+\([^;]*\);.*/\1/p' | head -1)
fi
[ -n "${CERT:-}" ] && [ -n "${KEY:-}" ] || die "не нашёл сертификат — передайте пути: $0 $DOMAIN $PORT /путь/fullchain.pem /путь/privkey.pem"
[ -f "$CERT" ] || die "файла сертификата нет: $CERT"
[ -f "$KEY" ] || die "файла ключа нет: $KEY"
ok "сертификат: $CERT"

# 3. покрывает ли сертификат наш домен
if command -v openssl >/dev/null; then
  NAMES=$(openssl x509 -in "$CERT" -noout -ext subjectAltName 2>/dev/null | tr ',' '\n' | sed 's/.*DNS://; s/[[:space:]]//g')
  COVERS=no
  for n in $NAMES; do
    case "$n" in
      "$DOMAIN") COVERS=yes ;;
      "*."*) case "$DOMAIN" in *".${n#\*.}") COVERS=yes ;; esac ;;
    esac
  done
  if [ "$COVERS" = "yes" ]; then ok "сертификат покрывает $DOMAIN"
  else warn "сертификат, похоже, НЕ покрывает $DOMAIN (имена: $(echo $NAMES | head -c 120))"; fi
fi

# 4. не занят ли этот server_name другим блоком
if nginx -T 2>/dev/null | grep -qE "^[[:space:]]*server_name[^;]*\b${DOMAIN//./\\.}\b"; then
  warn "в конфигурации уже есть server_name $DOMAIN — nginx выберет первый по порядку."
  warn "если после перезагрузки откроется старая страница, отключите прежний сайт:"
  warn "  ls /etc/nginx/sites-enabled/   и   rm /etc/nginx/sites-enabled/<старый>"
fi

# 5. пишем конфигурацию
cat > "$CONF" <<NGINX
# Создано scripts/https-behind-nginx.sh
server {
    listen 80;
    server_name $DOMAIN;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name $DOMAIN;

    ssl_certificate     $CERT;
    ssl_certificate_key $KEY;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:$PORT;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
    }
}
NGINX
ln -sf "$CONF" "$LINK"
ok "конфигурация записана: $CONF"

# 6. проверяем и перезагружаем; при ошибке откатываем
if nginx -t >/dev/null 2>&1; then
  systemctl reload nginx
  ok "nginx перезагружен"
else
  echo "--- вывод nginx -t ---"; nginx -t || true
  rm -f "$LINK" "$CONF"
  die "конфигурация не прошла проверку — изменения отменены, nginx не тронут"
fi

echo
echo "==> Проверьте:"
echo "    curl -s https://$DOMAIN/api/health"
echo "    curl -s -o /dev/null -w '%{http_code}\\n' http://$DOMAIN/   # ожидается 301"
