# Запуск без Docker: только jar

Подходит, чтобы **быстро проверить**, что сервер работает, или если Docker на сервере
ставить не хочется. Для постоянной работы с веб-версией и доменом удобнее Docker —
см. [`deployment.md`](deployment.md).

## Что важно понять сразу

| | Только jar | Docker (deployment.md) |
|---|---|---|
| API для Android-приложения | ✔ | ✔ |
| **Веб-версия в браузере** | ✘ — её нужно отдельно собрать и отдать через nginx | ✔ — nginx внутри compose |
| HTTPS и домен | настраиваете сами | общий шлюз + Let's Encrypt |
| Два контура (тест и рабочая) | вручную, двумя копиями | готово, `deploy.sh test/prod` |
| Резервные копии, обновления | вручную | `backup.sh`, `deploy.sh` |

Jar — это **сервер приложений**: он отдаёт API по адресу `http://<сервер>:8080/api/…`.
Статику веб-версии раздаёт nginx (`web/dist`), а в Android-приложении адрес API
задаётся в `android/gradle.properties`.

## Шаг 1. База данных

```bash
sudo -u postgres psql -c "CREATE USER dnd WITH PASSWORD 'придумайте-пароль';"
sudo -u postgres psql -c "CREATE DATABASE dnd OWNER dnd;"
```

Таблицы создаст сам сервер при первом запуске (Flyway, миграции V1–V3). Проверено на
PostgreSQL 15.19: `Successfully applied 3 migrations`.

## Шаг 2. Настройки

Два равнозначных способа — выберите один.

**Способ А (рекомендую): файл `application.yml` рядом с jar.** Возьмите готовый
шаблон [`server/application.example.yml`](../server/application.example.yml) и
заполните четыре строки: пароль базы, секрет JWT, внешний адрес, каталог файлов.

```bash
sudo mkdir -p /opt/dnd-memory /var/lib/dnd-memory/files
cd /opt/dnd-memory
sudo cp /путь/к/dnd-memory-server.jar .
sudo cp /путь/к/application.example.yml application.yml
sudo nano application.yml
```

**Способ Б: переменные окружения.**

```bash
export DND_DB_URL='jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified'
export DND_DB_USER=dnd
export DND_DB_PASSWORD='пароль базы'
export DND_JWT_SECRET="$(openssl rand -base64 48)"   # не короче 32 символов
export DND_PUBLIC_URL='http://203.0.113.10:8080'      # адрес, по которому вас видно
export DND_CORS_ORIGINS='http://203.0.113.10:8080'
export DND_FILES_DIR='/var/lib/dnd-memory/files'
export DND_ADMIN_TOKEN="$(openssl rand -hex 16)"      # для выдачи ссылок сброса пароля
```

| Переменная | Ключ в `application.yml` | Обязательно |
|---|---|---|
| `DND_DB_URL` | `spring.datasource.url` | да, с `?stringtype=unspecified` |
| `DND_DB_USER` / `DND_DB_PASSWORD` | `spring.datasource.username` / `password` | да |
| `DND_JWT_SECRET` | `dnd.jwt.secret` | **да**, ≥ 32 символов, иначе сервер не стартует |
| `DND_PUBLIC_URL` | `dnd.public-url` | да — попадает в ссылки приглашений и QR |
| `DND_CORS_ORIGINS` | `dnd.cors-origins` | да для веб-версии |
| `DND_FILES_DIR` | `dnd.files-dir` | да, если загружаете картинки |
| `DND_ADMIN_TOKEN` | `dnd.admin-token` | нет; без него админ-панель сброса отвечает 404 |
| `DND_PORT` | `server.port` | нет, по умолчанию 8080 |

## Шаг 3. Запуск и проверка

```bash
cd /opt/dnd-memory
java -Dfile.encoding=UTF-8 -jar dnd-memory-server.jar
```

Проверка в другом окне:

```bash
curl http://localhost:8080/api/health
# {"status":"UP","version":"1.0.0","time":"…"}
```

Полная проверка всех сценариев (нужен только `curl`):

```bash
DND_ADMIN_TOKEN='ваш ключ' ./scripts/smoke-api.sh http://localhost:8080
# ВСЕ ПРОВЕРКИ ПРОШЛИ
```

## Шаг 4. Автозапуск (systemd)

Чтобы сервер поднимался сам и переживал перезагрузку:

```bash
sudo useradd -r -s /usr/sbin/nologin dnd
sudo chown -R dnd:dnd /opt/dnd-memory /var/lib/dnd-memory
sudo cp /путь/к/scripts/dnd-memory.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now dnd-memory
systemctl status dnd-memory
journalctl -u dnd-memory -f          # логи
```

Юнит запускает jar из `/opt/dnd-memory` с `-Xmx768m` и перезапускает при падении.

## Если нужна веб-версия

Jar её не отдаёт. Варианты:

1. **Через Docker** — самый простой: nginx, оба контура, HTTPS и бэкапы уже
   настроены, см. [`deployment.md`](deployment.md).
2. **Вручную**: собрать статику (`cd web && npm ci && npm run build`) и отдать
   каталог `web/dist` через nginx, направив `/api/` на `127.0.0.1:8080` — конфиг
   можно взять из [`docker/nginx/nginx.app.conf.template`](../docker/nginx/nginx.app.conf.template).

## Частые проблемы

| Симптом | Причина |
|---|---|
| `DND_JWT_SECRET должен быть не короче 32 байт` | секрет короче 32 символов |
| `Connection to localhost:5432 refused` | PostgreSQL не запущен или слушает другой порт |
| `FATAL: password authentication failed for user "dnd"` | пароль в настройках не совпадает с базой |
| `column "attrs" does not exist` и подобное | в строке подключения нет `?stringtype=unspecified` |
| приложение стартует, но `/api/…` отвечает 401 на всё | истёк access-токен: обновите через `/api/auth/refresh` |
| `502` из nginx | сервер не запущен или слушает не тот порт |
| Русские буквы в логах превратились в `????` | запуск без `-Dfile.encoding=UTF-8` |
