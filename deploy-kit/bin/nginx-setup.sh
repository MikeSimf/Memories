#!/usr/bin/env bash
# Публикует экземпляры через уже установленный nginx с существующим сертификатом.
#   sudo bash bin/nginx-setup.sh 7f6a7dc97a7c.vps.myjino.ru
#   sudo bash bin/nginx-setup.sh example.com 8090 8091 /путь/fullchain.pem /путь/privkey.pem
#
# Рабочий экземпляр — на 443, тестовый — на 8443. Ничего не удаляет: если после
# правки `nginx -t` не проходит, добавленный файл убирается и nginx не перезагружается.
set -euo pipefail

DOMAIN="${1:?Укажите домен}"
PROD_PORT="${2:-8090}"
TEST_PORT="${3:-8091}"
CERT_ARG="${4:-}"
KEY_ARG="${5:-}"

CONF=/etc/nginx/sites-available/dnd-memory.conf
LINK=/etc/nginx/sites-enabled/dnd-memory.conf

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1"; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

[ "$(id -u)" = "0" ] || die "нужны права root: sudo bash $0 $DOMAIN"
command -v nginx >/dev/null || die "nginx не установлен"

echo "==> Домен $DOMAIN: рабочий на 443 (порт $PROD_PORT), тестовый на 8443 (порт $TEST_PORT)"

for pair in "рабочий:$PROD_PORT" "тестовый:$TEST_PORT"; do
  name="${pair%%:*}"; port="${pair##*:}"
  if curl -sf --max-time 5 "http://127.0.0.1:$port/api/health" >/dev/null; then
    ok "$name экземпляр отвечает на 127.0.0.1:$port"
  else
    warn "$name экземпляр не отвечает на 127.0.0.1:$port — будет 502, пока не запущен"
  fi
done

if [ -n "$CERT_ARG" ] && [ -n "$KEY_ARG" ]; then
  CERT="$CERT_ARG"; KEY="$KEY_ARG"
else
  CERT=$(nginx -T 2>/dev/null | sed -n 's/^[[:space:]]*ssl_certificate[[:space:]]\+\([^;]*\);.*/\1/p' | head -1)
  KEY=$(nginx -T 2>/dev/null | sed -n 's/^[[:space:]]*ssl_certificate_key[[:space:]]\+\([^;]*\);.*/\1/p' | head -1)
fi
[ -n "${CERT:-}" ] && [ -n "${KEY:-}" ] || die "сертификат не найден — передайте пути: $0 $DOMAIN $PROD_PORT $TEST_PORT /cert /key"
[ -f "$CERT" ] || die "нет файла сертификата: $CERT"
[ -f "$KEY" ] || die "нет файла ключа: $KEY"
ok "сертификат: $CERT"

if command -v openssl >/dev/null; then
  NAMES=$(openssl x509 -in "$CERT" -noout -ext subjectAltName 2>/dev/null | tr ',' '\n' | sed 's/.*DNS://; s/[[:space:]]//g')
  COVERS=no
  for n in $NAMES; do
    case "$n" in
      "$DOMAIN") COVERS=yes ;;
      "*."*) case "$DOMAIN" in *".${n#\*.}") COVERS=yes ;; esac ;;
    esac
  done
  [ "$COVERS" = "yes" ] && ok "сертификат покрывает $DOMAIN" || warn "сертификат, похоже, не покрывает $DOMAIN"
fi

if nginx -T 2>/dev/null | grep -qE "^[[:space:]]*server_name[^;]*\b${DOMAIN//./\\.}\b"; then
  warn "уже есть server_name $DOMAIN — nginx возьмёт первый по порядку;"
  warn "если откроется старая страница, отключите прежний сайт в /etc/nginx/sites-enabled/"
fi

# если конфигурация уже была (повторный запуск) — сохраняем её на случай отката
if [ -f "$CONF" ]; then cp "$CONF" "$CONF.bak"; fi

cat > "$CONF" <<NGINX
# Создано deploy-kit/bin/nginx-setup.sh
server {
    listen 80;
    server_name $DOMAIN;
    return 301 https://\$host\$request_uri;
}

server {
    listen 443 ssl;
    server_name $DOMAIN;

    ssl_certificate     $CERT;
    ssl_certificate_key $KEY;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:$PROD_PORT;
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
    }
}

server {
    listen 8443 ssl;
    server_name $DOMAIN;

    ssl_certificate     $CERT;
    ssl_certificate_key $KEY;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:$TEST_PORT;
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
ok "конфигурация: $CONF"

if nginx -t >/dev/null 2>&1; then
  systemctl reload nginx
  ok "nginx перезагружен"
else
  echo "--- nginx -t ---"; nginx -t || true
  if [ -f "$CONF.bak" ]; then
    mv "$CONF.bak" "$CONF"
    warn "восстановлена прежняя конфигурация $CONF"
  else
    rm -f "$LINK" "$CONF"
  fi
  die "конфигурация не прошла проверку — изменения отменены, nginx не тронут"
fi
rm -f "$CONF.bak"

echo
echo "==> Проверьте:"
echo "    curl -s https://$DOMAIN/api/health"
echo "    curl -s -k https://$DOMAIN:8443/api/health"
