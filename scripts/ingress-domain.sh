#!/usr/bin/env bash
# Управление доменами общего шлюза: добавление, удаление, список, перезагрузка.
#
#   ./scripts/ingress-domain.sh add dnd.example.com dnd-prod-web 80
#   ./scripts/ingress-domain.sh add test.dnd.example.com dnd-test-web 80
#   ./scripts/ingress-domain.sh remove dnd.example.com
#   ./scripts/ingress-domain.sh list
#
# Если сертификат Let's Encrypt для домена ещё не выпущен, конфигурация создаётся
# только для HTTP: сначала выпустите сертификат, затем повторите add.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
CONF_DIR="$ROOT/docker/ingress/conf.d"
CERT_ROOT="${CERT_ROOT:-/etc/letsencrypt/live}"

usage() {
  sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
  exit 1
}

reload() {
  if ! command -v docker >/dev/null 2>&1; then
    echo "==> Docker недоступен: конфигурация записана, но шлюз не перезагружен"
    return 0
  fi
  if docker ps --format '{{.Names}}' | grep -qx 'ingress-nginx'; then
    docker exec ingress-nginx nginx -t
    docker exec ingress-nginx nginx -s reload
    echo "==> Шлюз перечитал конфигурацию"
  else
    echo "==> Шлюз не запущен. Запустите: ./scripts/ingress-up.sh"
  fi
}

proxy_body() {
  local upstream="$1" port="$2"
  cat <<PROXY
    location / {
        proxy_pass http://${upstream}:${port};
        proxy_http_version 1.1;
        proxy_set_header Host \$host;
        proxy_set_header X-Real-IP \$remote_addr;
        proxy_set_header X-Forwarded-For \$proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto \$scheme;
        proxy_read_timeout 120s;
    }
PROXY
}

case "${1:-}" in
  add)
    DOMAIN="${2:?Укажите домен}"; UPSTREAM="${3:?Укажите имя контейнера}"; PORT="${4:-80}"
    mkdir -p "$CONF_DIR"
    if [[ -f "$CERT_ROOT/$DOMAIN/fullchain.pem" ]]; then
      {
        echo "# Домен $DOMAIN -> $UPSTREAM:$PORT (HTTPS, сертификат Let's Encrypt)"
        echo "server {"
        echo "    listen 80;"
        echo "    server_name $DOMAIN;"
        echo "    location /.well-known/acme-challenge/ { root /var/www/certbot; }"
        echo "    location / { return 301 https://\$host\$request_uri; }"
        echo "}"
        echo
        echo "server {"
        echo "    listen 443 ssl;"
        echo "    http2 on;"
        echo "    server_name $DOMAIN;"
        echo "    ssl_certificate     $CERT_ROOT/$DOMAIN/fullchain.pem;"
        echo "    ssl_certificate_key $CERT_ROOT/$DOMAIN/privkey.pem;"
        echo "    ssl_protocols TLSv1.2 TLSv1.3;"
        echo "    client_max_body_size 12m;"
        echo
        proxy_body "$UPSTREAM" "$PORT"
        echo "}"
      } > "$CONF_DIR/$DOMAIN.conf"
      echo "==> $DOMAIN -> $UPSTREAM:$PORT (HTTPS)"
    else
      {
        echo "# Домен $DOMAIN -> $UPSTREAM:$PORT (только HTTP: сертификата ещё нет)"
        echo "server {"
        echo "    listen 80;"
        echo "    server_name $DOMAIN;"
        echo "    location /.well-known/acme-challenge/ { root /var/www/certbot; }"
        echo "    client_max_body_size 12m;"
        echo
        proxy_body "$UPSTREAM" "$PORT"
        echo "}"
      } > "$CONF_DIR/$DOMAIN.conf"
      echo "==> $DOMAIN -> $UPSTREAM:$PORT (пока только HTTP)"
      echo "    Выпустите сертификат и повторите команду:"
      echo "    sudo certbot certonly --webroot -w $ROOT/docker/ingress/certbot-www -d $DOMAIN"
      echo "    ./scripts/ingress-domain.sh add $DOMAIN $UPSTREAM $PORT"
    fi
    reload
    ;;
  remove)
    DOMAIN="${2:?Укажите домен}"
    rm -f "$CONF_DIR/$DOMAIN.conf"
    echo "==> Домен $DOMAIN убран"
    reload
    ;;
  list)
    if compgen -G "$CONF_DIR/*.conf" > /dev/null; then
      grep -h -E "^# Домен" "$CONF_DIR"/*.conf || ls "$CONF_DIR"
    else
      echo "Домены не заданы."
    fi
    ;;
  reload)
    reload
    ;;
  *)
    usage
    ;;
esac
