# DnD Memory на сервере: Docker, два экземпляра, HTTPS

Комплект для развёртывания **без исходников на сервере**: только Docker, готовый jar и
эти скрипты. Рабочий и тестовый экземпляры независимы — свои базы, свои папки, свои порты.

```
Интернет ──► Apache или nginx сервера (уже стоит, сертификат хостера)
                 │  :443  ──► 127.0.0.1:8090 ──► dnd-prod-web ──┬─► dnd-prod-app ──► dnd-prod-db
                 │                                              └─ статика веб-версии
                 │  :8443 ──► 127.0.0.1:8091 ──► dnd-test-web ──┬─► dnd-test-app ──► dnd-test-db
                 │                                              └─ статика веб-версии
                 └─ :80   ──► redirect на https
```

## Структура папок

```
/opt/dnd-memory/
  prod/
    app.jar            ← сюда кладёте jar рабочей версии
    application.yml    ← настройки (трогать почти не нужно)
    .env               ← секреты: пароль базы, JWT-секрет, адреса
    web.conf           ← nginx внутри контейнера: статика + прокси /api
    data/files/        ← загруженные картинки (создаётся автоматически)
    start.sh stop.sh restart.sh logs.sh
  test/
    app.jar            ← сюда jar тестовой версии
    ... (то же самое)
  web/                 ← готовая веб-версия, одна на оба экземпляра
  bin/
    dnd.sh             ← общее управление
    start-all.sh       ← поднять всё настроенное
    install-docker.sh  ← установка Docker (шаг 1)
    apache-setup.sh    ← публикация по HTTPS через Apache
    nginx-setup.sh     ← публикация по HTTPS через nginx
    dnd-memory.service ← автозапуск через systemd
```

## Шаг 1. Docker

Распакуйте комплект в `/opt/dnd-memory` (см. шаг 2), затем:

```bash
cd /opt/dnd-memory
sudo bash bin/install-docker.sh
```

Скрипт ставит Docker Engine и плагин `compose`, включает автозапуск демона
(`systemctl enable --now docker`) и печатает версии. Чтобы работать без `sudo`:

```bash
sudo usermod -aG docker $USER
newgrp docker
```

Проверка:

```bash
docker --version && docker compose version
docker run --rm hello-world
```

<details>
<summary>То же самое вручную, если скрипт не подошёл</summary>

```bash
sudo apt-get update
sudo apt-get install -y ca-certificates curl gnupg
sudo install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg | sudo gpg --dearmor -o /etc/apt/keyrings/docker.gpg
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.gpg] \
https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo $VERSION_CODENAME) stable" \
  | sudo tee /etc/apt/sources.list.d/docker.list >/dev/null
sudo apt-get update
sudo apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
sudo usermod -aG docker $USER
newgrp docker
sudo systemctl enable --now docker
```

</details>

## Шаг 2. Папки и jar

```bash
# распаковка комплекта в /opt/dnd-memory (в архиве уже папки prod, test, bin)
sudo mkdir -p /opt/dnd-memory
sudo tar xzf dnd-memory-deploy-kit.tar.gz -C /opt/dnd-memory
sudo chown -R $USER:$USER /opt/dnd-memory
cd /opt/dnd-memory

# права на скрипты (нужны, если файлы попали на сервер не через tar)
chmod +x bin/*.sh prod/*.sh test/*.sh

# секреты для рабочего экземпляра
cp prod/.env.example prod/.env
nano prod/.env          # DB_PASSWORD, JWT_SECRET (openssl rand -base64 48), ADMIN_TOKEN

# то же для тестового
cp test/.env.example test/.env
nano test/.env

# положите свои сборки
cp /путь/к/dnd-memory-server.jar prod/app.jar
cp /путь/к/dnd-memory-server.jar test/app.jar

# запуск
./prod/start.sh
./test/start.sh

# проверка
./bin/dnd.sh prod health    # или: curl http://127.0.0.1:8090/api/health
./bin/dnd.sh test health
```

Первый запуск скачивает образ PostgreSQL и Java (~250 МБ) и создаёт таблицы — минуты
две. Дальше контейнеры поднимаются за секунды.

**Обновление версии** — просто замените jar и перезапустите:

```bash
cp новый-dnd-memory-server.jar prod/app.jar
./prod/restart.sh
```

`restart.sh` пересоздаёт контейнер, поэтому подхватывается новый файл.

**Обновление веб-версии.** Веб-часть — обычная статика в папке `web/`, одна на оба
экземпляра. Замените её содержимое и перезапустите контейнеры:

```bash
# web/ — результат сборки web-версии (index.html, assets/, sw.js …)
rm -rf /opt/dnd-memory/web/* && tar xzf web-dist.tar.gz -C /opt/dnd-memory/web
./prod/restart.sh && ./test/restart.sh
```

Если статику менять не нужно, шаг пропускается: комплект уже содержит актуальную
веб-версию.

**Если jar ещё не готов**, скрипт честно скажет: `Нет /opt/dnd-memory/prod/app.jar`.
Конфигурация при этом не ломается — можно подготовить всё заранее.

## Шаг 3. HTTPS

Сертификат выпускать не нужно: на сервере уже есть действующий (у Jino это
wildcard `*.vps.myjino.ru`), и он покрывает `7f6a7dc97a7c.vps.myjino.ru`.

Сначала посмотрите, кто держит порты 80 и 443:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b'
```

**Если это Apache** (на серверах Jino стоит он, Ubuntu 22.04 → `Apache/2.4.52`):

```bash
cd /opt/dnd-memory
sudo bash bin/apache-setup.sh 7f6a7dc97a7c.vps.myjino.ru
```

**Если это nginx:**

```bash
cd /opt/dnd-memory
sudo bash bin/nginx-setup.sh 7f6a7dc97a7c.vps.myjino.ru
```

Оба скрипта делают одно и то же:

* находят сертификат в текущей конфигурации веб-сервера и проверяют, что он покрывает домен;
* добавляют виртуальные хосты: **443** → рабочий экземпляр, **8443** → тестовый;
* перенаправляют HTTP на HTTPS;
* проверяют конфигурацию (`apache2ctl configtest` / `nginx -t`) и при ошибке
  **откатывают** свои изменения, не трогая остальные сайты.

Проверка:

```bash
curl -s https://7f6a7dc97a7c.vps.myjino.ru/api/health
curl -s https://7f6a7dc97a7c.vps.myjino.ru:8443/api/health
```

Нужные настройки на сервере, кроме этого: **открытый порт 8443** в файрволе, если он
включён:

```bash
sudo ufw allow 8443/tcp      # если ufw активен
sudo ufw status
```

Порты 80 и 443 уже заняты веб-сервером — их трогать не нужно, он же и раздаёт HTTPS.
Apache перезапускается (`systemctl restart apache2`), потому что добавлен новый
`Listen 8443`; nginx достаточно перезагрузить.

## Шаг 4. Чтобы работало всегда

Две вещи, независимые друг от друга:

1. **Политика перезапуска контейнеров.** В `docker-compose.yml` стоит
   `restart: unless-stopped`: контейнеры поднимаются вместе с Docker, в том числе
   после перезагрузки сервера. Достаточно, чтобы Docker был включён в автозапуск
   (`sudo systemctl enable docker` — сделано на шаге 1).
2. **Юнит systemd** — гарантирует, что после загрузки применён compose-файл:

```bash
sudo cp bin/dnd-memory.service /etc/systemd/system/
sudo systemctl daemon-reload
sudo systemctl enable --now dnd-memory
systemctl status dnd-memory
```

Проверка «настоящей» перезагрузки:

```bash
sudo reboot
# после загрузки:
docker ps                    # контейнеры dnd-prod-* и dnd-test-* в статусе Up
curl -s https://7f6a7dc97a7c.vps.myjino.ru/api/health
```

Терминал можно закрывать в любой момент — процессы живут в Docker.

## Шаг 5. Приложение

Тестовый экземпляр доступен по `https://7f6a7dc97a7c.vps.myjino.ru:8443/` — на него и
настроена сборка APK, которую я приложил. Сертификат доверенный, поэтому никаких
послаблений для HTTP не требуется.

Если понадобится собрать самому:

```bash
cd android
./gradlew assembleDebug -PDND_SERVER_URL=https://7f6a7dc97a7c.vps.myjino.ru:8443/
```

Для рабочего экземпляра — тот же ключ с адресом без порта:

```bash
./gradlew assembleDebug -PDND_SERVER_URL=https://7f6a7dc97a7c.vps.myjino.ru/
```

## Полезные команды

```bash
./prod/start.sh            # поднять
./prod/stop.sh             # остановить (тома с данными остаются)
./prod/restart.sh          # перезапустить после замены jar
./prod/logs.sh             # логи приложения и базы
./bin/dnd.sh prod status   # состояние контейнеров
./bin/dnd.sh prod update   # пересоздать только приложение (быстрее restart)
./bin/dnd.sh test health   # проверка, что тестовый отвечает
```

## Резервные копии

```bash
# база рабочего экземпляра
docker exec dnd-prod-db pg_dump -U dnd -d dnd | gzip > ~/dnd-prod-$(date +%F).sql.gz

# картинки
tar czf ~/dnd-files-$(date +%F).tar.gz -C /opt/dnd-memory/prod/data files
```

Данные базы лежат в томе `dnd-prod_pg-data` (не в папке), поэтому `stop.sh` их не
удаляет. Удалить данные насовсем — только `docker compose down -v`.

## Если что-то не так

| Симптом | Причина и решение |
|---|---|
| `Permission denied` на `./prod/start.sh` | у скриптов нет бита исполнения (файлы копировали не через `tar`): `chmod +x bin/*.sh prod/*.sh test/*.sh` |
| `Нет prod/app.jar` | положите jar в папку экземпляра |
| `Задайте DB_PASSWORD в .env` | не заполнен `.env` |
| `bind: address already in use` | порт 8090/8091 занят — поменяйте `APP_PORT` в `.env` |
| `502 Bad Gateway` на домене | экземпляр не запущен: `./prod/logs.sh` |
| `503 Service Unavailable` на домене | Apache не смог связаться с контейнером: `docker compose -p dnd-prod ps` |
| `✗ nginx не установлен` | на сервере Apache — запустите `sudo bash bin/apache-setup.sh <домен>` |
| контейнер падает с `password authentication failed` | в `.env` и в томе базы разные пароли: если меняли `DB_PASSWORD`, удалите том `docker compose -p dnd-prod down -v` (данные пропадут) |
| после перезагрузки ничего не поднялось | `sudo systemctl status docker dnd-memory` |
| на 8443 не открывается | порт закрыт файрволом: `sudo ufw allow 8443/tcp` |
| старая страница вместо приложения | конфликт `server_name` в nginx — см. предупреждение `nginx-setup.sh` |
