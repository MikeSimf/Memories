#!/usr/bin/env bash
# Публикует экземпляры через уже установленный Apache с существующим сертификатом.
#   sudo bash bin/apache-setup.sh 7f6a7dc97a7c.vps.myjino.ru
#   sudo bash bin/apache-setup.sh example.com 8090 8091 /путь/fullchain.pem /путь/privkey.pem
#
# Рабочий экземпляр — на 443, тестовый — на 8443. Ничего не удаляет: если после
# правки `apache2ctl configtest` не проходит, добавленный сайт и Listen убираются,
# Apache не перезапускается.
set -uo pipefail

DOMAIN="${1:?Укажите домен}"
PROD_PORT="${2:-8090}"
TEST_PORT="${3:-8091}"
CERT_ARG="${4:-}"
KEY_ARG="${5:-}"

SITE=dnd-memory
AVAIL=/etc/apache2/sites-available/$SITE.conf
ENABLED=/etc/apache2/sites-enabled/$SITE.conf
PORTS=/etc/apache2/ports.conf

ok()   { printf '  \033[32m✓\033[0m %s\n' "$1"; }
warn() { printf '  \033[33m!\033[0m %s\n' "$1" >&2; }
die()  { printf '  \033[31m✗\033[0m %s\n' "$1" >&2; exit 1; }

[ "$(id -u)" = "0" ] || die "нужны права root: sudo bash $0 $DOMAIN"
command -v apache2 >/dev/null || die "Apache не найден. Если стоит nginx — используйте bin/nginx-setup.sh"

echo "==> Домен $DOMAIN: рабочий на 443 (порт $PROD_PORT), тестовый на 8443 (порт $TEST_PORT)"

for pair in "рабочий:$PROD_PORT" "тестовый:$TEST_PORT"; do
  name="${pair%%:*}"; port="${pair##*:}"
  if curl -sf --max-time 5 "http://127.0.0.1:$port/api/health" >/dev/null; then
    ok "$name экземпляр отвечает на 127.0.0.1:$port"
  else
    warn "$name экземпляр не отвечает на 127.0.0.1:$port — будет 503, пока не запущен"
  fi
done

# --- сертификат ------------------------------------------------------------
# В Apache может быть несколько сертификатов (например, дефолтный snakeoil и
# сертификат хостера). Берём тот, который реально покрывает наш домен.
cert_covers() {
  local cert="$1" domain="$2" names n
  names=$(openssl x509 -in "$cert" -noout -ext subjectAltName 2>/dev/null \
          | tr ',' '\n' | sed 's/.*DNS://; s/[[:space:]]//g')
  for n in $names; do
    case "$n" in
      "$domain") return 0 ;;
      "*."*) case "$domain" in *".${n#\*.}") return 0 ;; esac ;;
    esac
  done
  return 1
}

# Пары «сертификат + ключ» из всех файлов конфигурации Apache
collect_pairs() {
  local f c k
  for f in /etc/apache2/sites-enabled/* /etc/apache2/sites-available/* \
           /etc/apache2/conf-enabled/*; do
    [ -f "$f" ] || continue
    c=$(grep -v '^[[:space:]]*#' "$f" \
        | sed -n 's/.*SSLCertificateFile[[:space:]]\+\([^[:space:]]*\).*/\1/p' | tail -1)
    k=$(grep -v '^[[:space:]]*#' "$f" \
        | sed -n 's/.*SSLCertificateKeyFile[[:space:]]\+\([^[:space:]]*\).*/\1/p' | tail -1)
    [ -n "$c" ] && [ -n "$k" ] && printf '%s %s\n' "$c" "$k"
  done
}

if [ -n "$CERT_ARG" ] && [ -n "$KEY_ARG" ]; then
  CERT="$CERT_ARG"; KEY="$KEY_ARG"
  ok "сертификат из аргументов"
else
  CERT=""; KEY=""; FALLBACK=""
  while read -r c k; do
    [ -f "$c" ] && [ -f "$k" ] || continue
    if command -v openssl >/dev/null && cert_covers "$c" "$DOMAIN"; then
      CERT="$c"; KEY="$k"; break
    fi
    [ -z "$FALLBACK" ] && FALLBACK="$c $k"
  done < <(collect_pairs | sort -u)
  if [ -z "$CERT" ] && [ -n "$FALLBACK" ]; then
    CERT="${FALLBACK% *}"; KEY="${FALLBACK#* }"
    warn "ни один найденный сертификат не покрывает $DOMAIN — беру $CERT"
  fi
fi
if [ -z "$CERT" ] || [ -z "$KEY" ]; then
  echo "  Что нашлось в конфигурации Apache:" >&2
  grep -rh 'SSLCertificate' /etc/apache2/sites-enabled /etc/apache2/sites-available \
       /etc/apache2/conf-enabled 2>/dev/null | sed 's/^/    /' >&2
  die "сертификат не найден — передайте пути: $0 $DOMAIN $PROD_PORT $TEST_PORT /cert /key"
fi
[ -f "$CERT" ] || die "нет файла сертификата: $CERT"
[ -f "$KEY" ] || die "нет файла ключа: $KEY"
ok "сертификат: $CERT"
ok "ключ:        $KEY"
if command -v openssl >/dev/null; then
  if cert_covers "$CERT" "$DOMAIN"; then
    ok "сертификат покрывает $DOMAIN"
  else
    warn "сертификат, похоже, не покрывает $DOMAIN — браузер может ругаться"
  fi
fi

# --- модули ----------------------------------------------------------------
echo "==> включаю модули Apache"
for m in ssl proxy proxy_http headers; do
  if a2enmod -q "$m" 2>/dev/null; then ok "mod_$m"; else warn "mod_$m уже включён или недоступен"; fi
done

# --- порт 8443 -------------------------------------------------------------
PORTS_BAK=""
if grep -qE "^[[:space:]]*Listen[[:space:]]+8443([[:space:]]|$)" "$PORTS" 2>/dev/null; then
  ok "порт 8443 уже слушается (ports.conf)"
else
  cp "$PORTS" "$PORTS.bak"
  PORTS_BAK="$PORTS.bak"
  printf '\n# DnD Memory: тестовый экземпляр\nListen 8443\n' >> "$PORTS"
  ok "добавлен Listen 8443 в $PORTS"
fi

# --- конфигурация сайта ----------------------------------------------------
EXISTED=no
[ -f "$AVAIL" ] && { EXISTED=yes; cp "$AVAIL" "$AVAIL.bak"; }

cat > "$AVAIL" <<APACHE
# Создано deploy-kit/bin/apache-setup.sh
# Рабочий экземпляр DnD Memory: всё уходит в веб-контейнер на 127.0.0.1:$PROD_PORT
<VirtualHost *:80>
    ServerName $DOMAIN
    Redirect permanent / https://$DOMAIN/
</VirtualHost>

<VirtualHost *:443>
    ServerName $DOMAIN
    SSLEngine on
    SSLCertificateFile $CERT
    SSLCertificateKeyFile $KEY

    LimitRequestBody 13107200
    ProxyPreserveHost On
    ProxyRequests Off
    ProxyPass / http://127.0.0.1:$PROD_PORT/ connectiontimeout=10 timeout=120
    ProxyPassReverse / http://127.0.0.1:$PROD_PORT/
    RequestHeader set X-Forwarded-Proto "https"

    ErrorLog \${APACHE_LOG_DIR}/dnd-prod-error.log
    CustomLog \${APACHE_LOG_DIR}/dnd-prod-access.log combined
</VirtualHost>

# Тестовый экземпляр DnD Memory: порт 8443 -> 127.0.0.1:$TEST_PORT
<VirtualHost *:8443>
    ServerName $DOMAIN
    SSLEngine on
    SSLCertificateFile $CERT
    SSLCertificateKeyFile $KEY

    LimitRequestBody 13107200
    ProxyPreserveHost On
    ProxyRequests Off
    ProxyPass / http://127.0.0.1:$TEST_PORT/ connectiontimeout=10 timeout=120
    ProxyPassReverse / http://127.0.0.1:$TEST_PORT/
    RequestHeader set X-Forwarded-Proto "https"

    ErrorLog \${APACHE_LOG_DIR}/dnd-test-error.log
    CustomLog \${APACHE_LOG_DIR}/dnd-test-access.log combined
</VirtualHost>
APACHE
ok "конфигурация: $AVAIL"

a2ensite -q "$SITE" >/dev/null 2>&1 || true
ok "сайт включён: $ENABLED"

# --- проверка и откат ------------------------------------------------------
rollback() {
  rm -f "$ENABLED"
  if [ -f "$AVAIL.bak" ]; then mv "$AVAIL.bak" "$AVAIL"; else rm -f "$AVAIL"; fi
  if [ -n "$PORTS_BAK" ] && [ -f "$PORTS_BAK" ]; then mv "$PORTS_BAK" "$PORTS"; fi
}

if apache2ctl configtest >/dev/null 2>&1; then
  ok "apache2ctl configtest пройден"
else
  echo "--- apache2ctl configtest ---" >&2
  apache2ctl configtest 2>&1 | sed 's/^/    /' >&2
  rollback
  die "конфигурация не прошла проверку — изменения отменены, Apache не перезапущен"
fi

if systemctl restart apache2 >/dev/null 2>&1; then
  ok "Apache перезапущен"
else
  warn "не удалось перезапустить apache2"
  systemctl status apache2 --no-pager -l 2>&1 | tail -15 >&2
  rollback
  die "откатил изменения: apache2 не поднялся с новой конфигурацией"
fi
rm -f "$AVAIL.bak"

sleep 1
for target in "https://$DOMAIN/" "https://$DOMAIN:8443/"; do
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 10 "$target" || true)
  if [ "$code" = "200" ]; then ok "$target -> 200"; else warn "$target -> ${code:-нет ответа}"; fi
done

echo
echo "==> Проверьте:"
echo "    curl -s https://$DOMAIN/api/health"
echo "    curl -s https://$DOMAIN:8443/api/health"
echo "    журналы: /var/log/apache2/dnd-prod-error.log, dnd-test-error.log"
