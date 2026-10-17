# Развёртывание за уже существующим nginx (HTTPS от хостера)

Подходит, когда на VPS уже стоит nginx/Apache и есть действующий сертификат — так
обычно бывает у хостингов вроде Jino: выдают wildcard-сертификат вида
`*.vps.myjino.ru`, и HTTPS работает сразу, без Let's Encrypt.

В этом случае Docker-шлюз на 80/443 **не нужен и не встанет** (порты заняты).
Наша веб-версия публикуется на локальный порт, а внешний nginx проксирует на него.

```
Интернет ──► nginx хостера (:80, :443, сертификат *.vps.myjino.ru)
                    │  server_name 7f6a7dc97a7c.vps.myjino.ru
                    ▼
             127.0.0.1:8090  ← контейнер dnd-prod-web (статика + прокси /api)
                    ▼
             dnd-prod-server ──► dnd-prod-db
```

## 1. Понять, что занято

```bash
sudo ss -ltnp | grep -E ':80|:443'
sudo nginx -T 2>/dev/null | grep -E "server_name|listen|ssl_certificate" | head -30
ls /etc/nginx/sites-enabled/ 2>/dev/null
curl -sI https://ваш-домен/ | head -3
```

Из вывода `nginx -T` нужны два пути: `ssl_certificate` и `ssl_certificate_key` —
их пропишем в свой vhost. Если `ss` показывает `docker-proxy`, значит на 80/443 уже
стоит контейнер, и его нужно либо остановить, либо проксировать на него.

## 2. Запуск контура

```bash
cd /opt/apps/dnd-memory

cp docker/env.prod.example docker/env.prod
nano docker/env.prod
```

Заполните:

| Переменная | Значение |
|---|---|
| `DOMAIN` | домен без схемы, например `7f6a7dc97a7c.vps.myjino.ru` |
| `CORS_ORIGINS` | `https://7f6a7dc97a7c.vps.myjino.ru` |
| `DB_PASSWORD` | пароль базы контейнера (придумайте) |
| `JWT_SECRET` | `openssl rand -base64 48` |
| `WEB_BIND` / `WEB_PORT` | `127.0.0.1` / `8090` — локальный вход для nginx хостера |

```bash
sudo docker network create edge     # нужна compose-файлу, даже без шлюза
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml up -d --build
curl http://127.0.0.1:8090/api/health
```

Если `health` отвечает — контур поднялся, дальше только внешний nginx.

## 3. Виртуальный хост в существующем nginx

Создайте файл, например `/etc/nginx/sites-available/dnd-memory.conf`, и включите его
симлинком в `sites-enabled`:

```nginx
server {
    listen 80;
    server_name 7f6a7dc97a7c.vps.myjino.ru;
    return 301 https://$host$request_uri;
}

server {
    listen 443 ssl;
    http2 on;
    server_name 7f6a7dc97a7c.vps.myjino.ru;

    # пути возьмите из вывода `sudo nginx -T | grep ssl_certificate`
    ssl_certificate     /путь/к/fullchain.pem;
    ssl_certificate_key /путь/к/privkey.pem;

    client_max_body_size 12m;

    location / {
        proxy_pass http://127.0.0.1:8090;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 120s;
    }
}
```

```bash
sudo ln -s /etc/nginx/sites-available/dnd-memory.conf /etc/nginx/sites-enabled/
sudo nginx -t && sudo systemctl reload nginx
```

## 4. Проверка

```bash
curl https://7f6a7dc97a7c.vps.myjino.ru/api/health
DND_ADMIN_TOKEN='ваш ключ' ./scripts/smoke-api.sh https://7f6a7dc97a7c.vps.myjino.ru
```

## 5. Приложение

Теперь адрес HTTPS, и никаких послаблений для открытого HTTP не нужно:

```
DND_SERVER_URL=https://7f6a7dc97a7c.vps.myjino.ru/
```

`./gradlew assembleDebug` — и APK готов к установке.

## Подводные камни

| Симптом | Причина |
|---|---|
| `bind: address already in use` при `docker compose up` | поменяйте `WEB_PORT` (8090 занят) |
| `502 Bad Gateway` на домене | контейнер `dnd-prod-web` не поднялся: `docker compose -p dnd-prod logs web` |
| Сайт отдаёт старую страницу хостера | наш vhost не выиграл по `server_name`; проверьте `sudo nginx -T \| grep -A3 server_name`, нет ли `default_server` |
| Правки vhost пропадают | конфигурацией управляет панель (ISPmanager и т.п.) — добавляйте домен через неё |
| Сертификат не подходит | wildcard `*.vps.myjino.ru` покрывает только этот домен; для своего нужен certbot |
| `413 Request Entity Too Large` при загрузке картинки | `client_max_body_size` в nginx хостера меньше 12 МБ |
