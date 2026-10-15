# Развёртывание DnD Memory на своём сервере

Пошаговая инструкция для Ubuntu 22.04/24.04 или Debian 12. Если на сервере уже есть
другие проекты — сначала прочитайте [`server-multi-project.md`](server-multi-project.md):
там про общий шлюз, реестр портов и соглашение об именах.

```
Интернет ──► ingress-nginx (:80, :443) ──► dnd-prod-web ──► dnd-prod-server ──► dnd-prod-db
                     │                                                              (том)
                     └── по домену отправляет запрос в нужный контейнер

           :8081 ─────────────────────────► dnd-test-web ──► dnd-test-server ──► dnd-test-db
                                                              (отдельные том и база)
```

Тестовый и рабочий контуры независимы: разные compose-проекты (`dnd-test`, `dnd-prod`),
разные тома, разные базы, разные секреты. Падение одного не влияет на другой.

## 0. Что нужно

* Ubuntu 22.04/24.04 или Debian 12, доступ по SSH с правами `sudo`.
* 2 ГБ RAM (сервер приложений ограничен 512–768 МБ, PostgreSQL ~256 МБ).
* 10 ГБ свободного места.
* Открытые порты: `22`, `80`, `443`, `8081` (тестовый контур).

## 1. Установка Docker (один раз на сервер)

```bash
sudo mkdir -p /opt/apps
sudo chown -R $USER:$USER /opt/apps
# положите код проекта в /opt/apps/dnd-memory (шаг 2), затем:
sudo bash /opt/apps/dnd-memory/scripts/install-docker.sh
```

Скрипт ставит Docker Engine, containerd и плагин `docker compose` из официального
репозитория Docker. Проверка:

```bash
docker --version
docker compose version
```

Если работаете не под root — добавьте себя в группу, иначе каждая команда потребует
`sudo`:

```bash
sudo usermod -aG docker $USER && newgrp docker
```

## 2. Как положить код на сервер

**Вариант А — архив из чата:**

```bash
scp dnd-memory-1.0.0.tar.gz user@сервер:/tmp/
ssh user@сервер
sudo mkdir -p /opt/apps && cd /opt/apps
sudo tar xzf /tmp/dnd-memory-1.0.0.tar.gz
sudo chown -R $USER:$USER /opt/apps/dnd-memory
```

**Вариант Б — git** (репозиторий проекта: <https://github.com/MikeSimf/Memories>):

```bash
cd /opt/apps && git clone https://github.com/MikeSimf/Memories.git dnd-memory
```

Дальше все команды выполняются из `/opt/apps/dnd-memory`.

## 3. Общая сеть и шлюз

```bash
cd /opt/apps/dnd-memory
sudo docker network create edge     # один раз на весь сервер
./scripts/ingress-up.sh             # поднимет ingress-nginx на 80 и 443
```

Проверка:

```bash
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
# ingress-nginx   Up ...   0.0.0.0:80->80/tcp, 0.0.0.0:443->443/tcp
```

Пока домены не добавлены, шлюз отвечает `404` — это нормально.

## 4. Тестовый контур

```bash
cp docker/env.test.example docker/env.test
nano docker/env.test
./scripts/deploy.sh test
```

Что заполнить:

| Переменная | Значение |
|---|---|
| `DB_PASSWORD` | пароль базы, придумайте свой |
| `JWT_SECRET` | случайная строка **не короче 32 символов**: `openssl rand -base64 48` |
| `PUBLIC_URL` | адрес тестового контура, например `http://203.0.113.10:8081` |
| `CORS_ORIGINS` | тот же адрес; несколько — через запятую |
| `TEST_PORT` | порт тестового контура, по умолчанию `8081` |

Проверка:

```bash
curl http://localhost:8081/api/health      # {"status":"UP",...}
```

Откройте `http://<адрес сервера>:8081` — это веб-версия. Если порт закрыт файрволом:
`sudo ufw allow 8081/tcp`.

Логи: `docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml logs -f server`

## 5. Рабочий контур с доменом и HTTPS

### 5.1. Домен

Направьте A-запись домена (например `dnd.example.com`) на IP сервера:

```bash
dig +short dnd.example.com
```

### 5.2. Окружение

```bash
cp docker/env.prod.example docker/env.prod
nano docker/env.prod
```

| Переменная | Значение |
|---|---|
| `DOMAIN` | домен без схемы: `dnd.example.com` |
| `CORS_ORIGINS` | `https://dnd.example.com` |
| `DB_PASSWORD` | свой пароль базы |
| `JWT_SECRET` | своя случайная строка ≥ 32 символов |
| `SERVER_HEAP_MB` | память JVM, по умолчанию `768` |

```bash
./scripts/deploy.sh prod
```

Рабочий контур **не публикует порты** — он виден шлюзу по сети `edge`.

### 5.3. Сертификат

```bash
sudo apt-get install -y certbot
sudo certbot certonly --webroot \
  -w /opt/apps/dnd-memory/docker/ingress/certbot-www \
  -d dnd.example.com --agree-tos -m you@example.com --no-eff-email
sudo ls /etc/letsencrypt/live/dnd.example.com/     # fullchain.pem, privkey.pem
```

Каталог `docker/ingress/certbot-www` примонтирован в шлюз, поэтому проверка
`http://<домен>/.well-known/acme-challenge/…` работает при запущенном шлюзе.

Продление — системный таймер `certbot.timer`: `systemctl list-timers | grep certbot`.

### 5.4. Подключить домен к шлюзу

```bash
./scripts/ingress-domain.sh add dnd.example.com dnd-prod-web 80
curl -I https://dnd.example.com
```

Скрипт сам определяет, выпущен ли сертификат: без него создаст конфигурацию только
для HTTP, с ним — полную с редиректом на HTTPS. Если добавляли домен до выпуска
сертификата — повторите `add`, и он переключится на HTTPS.

Тестовый контур тоже можно повесить на домен:

```bash
./scripts/ingress-domain.sh add test.dnd.example.com dnd-test-web 80
```

## 6. Внешние входы (Яндекс ID и VK ID)

Оба бесплатны, но требуют регистрации приложения у провайдера. Пока
`YANDEX_CLIENT_ID`/`VK_CLIENT_ID` пусты, кнопки входа в интерфейсе неактивны —
регистрация по почте и паролю работает всегда.

**Яндекс ID**

1. <https://oauth.yandex.ru/client/new> — приложение типа «Веб-сервисы».
2. Redirect URI: `https://<домен>/oauth/callback` (веб) и `dndmemory://oauth` (Android).
3. Доступы: `login:email`, `login:info`.
4. Пропишите `YANDEX_CLIENT_ID` и `YANDEX_CLIENT_SECRET` в `docker/env.prod` и
   выполните `./scripts/deploy.sh prod`.

**VK ID**

1. <https://id.vk.com/business/go> — создайте приложение.
2. Redirect URI те же два.
3. VK ID требует PKCE: код-верификатор генерирует клиент и передаёт его серверу в
   поле `codeVerifier` при обмене кода.

## 7. Обновление версии

```bash
cd /opt/apps/dnd-memory
git pull                       # или распакуйте новый архив
./scripts/deploy.sh prod       # пересоберёт образы и перезапустит только этот контур
```

Миграции базы (Flyway) применяются автоматически при старте сервера. Тестовый
контур обновляется командой `./scripts/deploy.sh test`; соседние проекты не
затрагиваются.

## 8. Резервные копии

```bash
./scripts/backup.sh prod       # backups/dnd-prod-2025-01-01_120000.sql.gz
./scripts/restore.sh prod backups/dnd-prod-2025-01-01_120000.sql.gz
```

Ежедневно в 4 утра (crontab пользователя, от которого работает Docker):

```cron
0 4 * * * cd /opt/apps/dnd-memory && ./scripts/backup.sh prod >> backups/backup.log 2>&1
```

Файлы изображений лежат в томе `dnd-prod_files`; копия:

```bash
docker run --rm -v dnd-prod_files:/data -v "$PWD/backups:/backup" alpine \
  tar czf /backup/files-prod-$(date +%F).tar.gz -C /data .
```

**Раз в неделю забирайте копии с сервера**: том Docker переживёт перезапуск
контейнеров, но не потерю диска.

## 9. Полезные команды

```bash
# состояние контуров
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml ps
docker compose -p dnd-test --env-file docker/env.test -f docker/compose.test.yml ps

# логи
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml logs -f server
docker compose -p ingress -f docker/ingress/compose.yml logs -f

# консоль базы
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml exec db psql -U dnd -d dnd

# остановить / поднять
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml down
docker compose -p dnd-prod --env-file docker/env.prod -f docker/compose.prod.yml up -d

# домены шлюза
./scripts/ingress-domain.sh list
```

Данные контуров лежат в томах `dnd-test_db-data`, `dnd-prod_db-data`,
`dnd-test_files`, `dnd-prod_files`. Команда `down` их **не** удаляет; `down -v`
удаляет — не запускайте её на рабочем контуре.

## 10. Частые проблемы

| Симптом | Причина и решение |
|---|---|
| `Задайте JWT_SECRET` при старте compose | не заполнен `docker/env.<контур>` |
| `IllegalStateException: DND_JWT_SECRET должен быть не короче 32 байт` | секрет короче 32 символов |
| `network edge declared as external, but could not be found` | `docker network create edge`, затем `./scripts/ingress-up.sh` |
| `502 Bad Gateway` на домене | контейнер `dnd-prod-web` не поднялся или не в сети `edge`: `docker compose -p dnd-prod logs web` |
| `bind: address already in use` на 80/443 | порт занят системным nginx: `sudo ss -ltnp` и остановите лишнее |
| Домен открывается по HTTP, но не по HTTPS | сертификат не выпущен либо `add` сделан раньше него — выпустите и повторите `add` |
| `ERR_CERT_*` в браузере | `DOMAIN` не совпадает с сертификатом |
| После обновления просит войти заново | сменился `JWT_SECRET`: все access-токены инвалидированы |
| Тестовый контур недоступен снаружи | `sudo ufw allow 8081/tcp` |
