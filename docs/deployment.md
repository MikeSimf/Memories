# Развёртывание на своём сервере

Здесь пошагово: два контура на одном сервере, тестовый и рабочий, в Docker.

```
Интернет
   │
   ├─ :8081 ──► dnd-test-web (nginx + SPA) ──► dnd-test-server (Java) ──► dnd-test-db (PostgreSQL)
   │
   └─ :80/:443 ► dnd-prod-web (nginx + SPA) ──► dnd-prod-server (Java) ──► dnd-prod-db (PostgreSQL)
```

Контуры полностью независимы: разные compose-проекты (`dnd-test`, `dnd-prod`),
разные тома, разные базы, разные секреты. Падение или перезапуск одного не влияет
на другой.

## 0. Что нужно от сервера

* Ubuntu 22.04/24.04 или Debian 12 (подойдёт любой Linux с Docker).
* 2 ГБ RAM минимум (сервер приложений ограничен 512–768 МБ, PostgreSQL ~256 МБ).
* 10 ГБ свободного места.
* Открытые порты: `22` (ssh), `80`, `443` (рабочий контур), `8081` (тестовый контур).

## 1. Установка Docker (один раз)

Скопируйте репозиторий на сервер и запустите:

```bash
sudo bash scripts/install-docker.sh
```

Скрипт ставит Docker Engine, containerd и плагин `docker compose` из официального
репозитория Docker. Проверка:

```bash
docker --version
docker compose version
```

Если работаете не под root — добавьте себя в группу:

```bash
sudo usermod -aG docker $USER && newgrp docker
```

## 2. Тестовый контур

```bash
cp docker/env.test.example docker/env.test
nano docker/env.test      # заполните DB_PASSWORD, JWT_SECRET, PUBLIC_URL, CORS_ORIGINS
./scripts/deploy.sh test
```

Что важно заполнить:

| Переменная | Значение |
|---|---|
| `DB_PASSWORD` | пароль базы, придумайте свой |
| `JWT_SECRET` | случайная строка **не короче 32 символов**: `openssl rand -base64 48` |
| `PUBLIC_URL` | адрес, по которому тестовый контур доступен, например `http://203.0.113.10:8081` |
| `CORS_ORIGINS` | тот же адрес; список через запятую, если адресов несколько |
| `TEST_PORT` | порт тестового контура, по умолчанию `8081` |

После запуска проверьте:

```bash
curl http://localhost:8081/api/health     # {"status":"UP",...}
docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml logs -f server
```

Откройте `http://<адрес сервера>:8081` — это веб-версия тестового контура.

## 3. Рабочий контур с доменом и HTTPS

### 3.1. Домен

Направьте A-запись домена (например `dnd.example.com`) на IP сервера. Проверьте:

```bash
dig +short dnd.example.com
```

### 3.2. Сертификат Let's Encrypt

Сертификат выпускается на хосте (не в контейнере) и монтируется в nginx:

```bash
sudo apt-get install -y certbot
sudo mkdir -p /var/www/certbot
sudo certbot certonly --standalone -d dnd.example.com --agree-tos -m you@example.com --no-eff-email
sudo ls /etc/letsencrypt/live/dnd.example.com/    # fullchain.pem, privkey.pem
```

Продление — системный таймер `certbot.timer` (`systemctl list-timers | grep certbot`).
Так как на время выпуска 80-й порт занимает certbot, делайте это **до** первого
запуска рабочего контура. Для продления при уже занятом 80-м порту используйте
webroot-режим:

```bash
sudo certbot certonly --webroot -w /var/www/certbot -d dnd.example.com
```

Каталог `docker/certbot-www` уже примонтирован в контейнер как `/var/www/certbot`,
поэтому проверка `/.well-known/acme-challenge/` работает и при запущенном контуре.

### 3.3. Запуск

```bash
cp docker/env.prod.example docker/env.prod
nano docker/env.prod      # DOMAIN, CORS_ORIGINS, DB_PASSWORD, JWT_SECRET
./scripts/deploy.sh prod
```

| Переменная | Значение |
|---|---|
| `DOMAIN` | ваш домен без схемы, например `dnd.example.com` |
| `CORS_ORIGINS` | `https://dnd.example.com` |
| `DB_PASSWORD` | свой пароль базы |
| `JWT_SECRET` | своя случайная строка ≥ 32 символов |
| `SERVER_HEAP_MB` | память JVM, по умолчанию `768` |

Проверка: `https://dnd.example.com/api/health` и `https://dnd.example.com`.

### 3.4. Обновление версии

```bash
cd /path/to/dnd-memory
git pull
./scripts/deploy.sh prod        # пересоберёт образы и перезапустит
```

Миграции базы (Flyway) применяются автоматически при старте сервера.

## 4. Внешние входы (Яндекс ID и VK ID)

Оба варианта бесплатны, но требуют регистрации приложения у провайдера. Пока
`YANDEX_CLIENT_ID`/`VK_CLIENT_ID` пусты, кнопки входа в интерфейсе неактивны —
регистрация по почте и паролю работает всегда.

**Яндекс ID**

1. <https://oauth.yandex.ru/client/new> — создайте приложение типа «Веб-сервисы».
2. Redirect URI: `https://<ваш домен>/oauth/callback` (для веба) и `dndmemory://oauth`
   (для Android-приложения).
3. Доступы: `login:email`, `login:info`.
4. Полученные ClientID и Client secret положите в `docker/env.prod`:

```
YANDEX_CLIENT_ID=...
YANDEX_CLIENT_SECRET=...
```

**VK ID**

1. <https://id.vk.com/business/go> — создайте приложение.
2. Redirect URI те же: `https://<домен>/oauth/callback` и `dndmemory://oauth`.
3. VK ID требует PKCE: код-верификатор генерирует клиент, сервер принимает его в поле
   `codeVerifier` при обмене кода (`POST /api/auth/oauth/vk/callback`).

После правки файла контура: `./scripts/deploy.sh prod`.

## 5. Резервные копии

```bash
./scripts/backup.sh prod     # backups/dnd-prod-2025-01-01_120000.sql.gz
./scripts/restore.sh prod backups/dnd-prod-2025-01-01_120000.sql.gz
```

Ежедневная копия в 4 утра (crontab пользователя, от которого работает Docker):

```cron
0 4 * * * cd /opt/dnd-memory && ./scripts/backup.sh prod >> backups/backup.log 2>&1
```

Копии складываются в `backups/` рядом с репозиторием. **Раз в неделю забирайте их
с сервера** — том Docker переживёт перезапуск контейнеров, но не потерю диска.

Резервируются только база и (`docker`-том с файлами: `dnd-prod_files`). Файлы
изображений можно копировать так:

```bash
docker run --rm -v dnd-prod_files:/data -v "$PWD/backups:/backup" alpine \
  tar czf /backup/files-prod-$(date +%F).tar.gz -C /data .
```

## 6. Полезные команды

```bash
# состояние контура
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml ps

# логи сервера и nginx
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml logs -f server
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml logs -f web

# консоль базы
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml exec db psql -U dnd -d dnd

# остановить / поднять
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml down
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml up -d

# полная остановка вместе с тестовым контуром
docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml down
```

Данные контуров лежат в томах `dnd-test_db-data`, `dnd-prod_db-data`,
`dnd-test_files`, `dnd-prod_files`. Команда `down` их **не** удаляет;
`down -v` удаляет — не запускайте её на рабочем контуре.

## 7. Частые проблемы

| Симптом | Причина и решение |
|---|---|
| `Задайте JWT_SECRET` при старте compose | не заполнен `docker/env.<контур>` |
| Сервер перезапускается, в логах `IllegalStateException: DND_JWT_SECRET должен быть не короче 32 байт` | секрет короче 32 символов |
| `502 Bad Gateway` на `/api/*` | сервер ещё стартует (40–60 с) или упал: смотрите `logs server` |
| `ERR_CERT_*` в браузере | сертификат не выпущен или домен в `DOMAIN` не совпадает с сертификатом |
| Ошибка `bind: address already in use` | порт занят другим сервисом: смените `TEST_PORT` или остановите старый контур |
| Приложение не пускает в аккаунт после обновления | сменился `JWT_SECRET`: все access-токены инвалидированы, нужно войти заново |
