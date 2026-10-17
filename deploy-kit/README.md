# DnD Memory на сервере: Docker, два экземпляра, HTTPS

Комплект для развёртывания **без исходников на сервере**: только Docker, готовый jar и
эти скрипты. Рабочий и тестовый экземпляры независимы — свои базы, свои папки, свои порты.

## Скачать

Готовые файлы лежат в релизе, авторизация не нужна:

```bash
# комплект (эти скрипты и веб-версия)
curl -fL -o /tmp/kit.tar.gz \
  https://github.com/MikeSimf/Memories/releases/download/v1.0.0/dnd-memory-deploy-kit.tar.gz

# сервер приложения
curl -fL -o /tmp/dnd-memory-server.jar \
  https://github.com/MikeSimf/Memories/releases/download/v1.0.0/dnd-memory-server.jar

sha256sum /tmp/kit.tar.gz /tmp/dnd-memory-server.jar   # сверить с релизом
```

Копия архива есть и в самом репозитории: [`release/dnd-memory-deploy-kit.tar.gz`](../release/dnd-memory-deploy-kit.tar.gz)
(пересобирается скриптом [`scripts/make-deploy-kit.sh`](../scripts/make-deploy-kit.sh)).

```
Интернет ──► Apache или nginx сервера (уже стоит, сертификат хостера)
                 │  :80   ──► dnd-prod-web ──┬─► dnd-prod-app ──► dnd-prod-db
                 │                                              └─ статика веб-версии
                 │  :8091 ──► dnd-test-web ──┬─► dnd-test-app ──► dnd-test-db
                 │                                              └─ статика веб-версии
                 └─ TLS снимает фронт провайдера (или ваш веб-сервер, вариант B)
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
./bin/dnd.sh prod health    # или: curl http://127.0.0.1/api/health
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

Сначала выясните, как устроен вход на сервер — от этого зависит, нужен ли вообще
веб-сервер на VPS:

```bash
sudo ss -ltnp | grep -E ':(80|443)\b'
curl -sI http://127.0.0.1/api/health    # отвечает ли уже наш контейнер
```

### Вариант A. TLS делает провайдер, а 443 занят другим сервисом

Так на VPS у Джино: домен `*.vps.myjino.ru` обслуживает их фронт, он же держит
сертификат Let's Encrypt и ходит на **порт 80** вашей машины. Порт 443 при этом
может быть занят чем угодно — например, VPN (`amnezia-xray`), и трогать его нельзя.

В этом случае **ничего настраивать не нужно**: рабочий экземпляр уже опубликован на
`80` (в `prod/.env` стоит `APP_PORT=80`), и `https://<домен>/` открывается через фронт.

```bash
./prod/start.sh          # публикует 80
./test/start.sh          # публикует 8091
curl -s http://127.0.0.1/api/health
curl -s https://7f6a7dc97a7c.vps.myjino.ru/api/health
```

Тестовый экземпляр доступен **по IP машины**, а не по домену:
`http://81.177.139.30:8091/`. Домен `*.vps.myjino.ru` резолвится не на VPS, а на фронт
провайдера, и тот пробрасывает только 80 и 443 — путь `:8091` по домену просто не
доходит. Своего сертификата у теста нет, а брать провайдерский нельзя: его ключ лежит
у провайдера. Поэтому `PUBLIC_URL` и `CORS_ORIGINS` в `test/.env` указывают на IP.

Проверьте, что Apache не перехватит порт 80 после перезагрузки:

```bash
sudo systemctl disable --now apache2
```

Если `80` и `8091` не открываются снаружи — проверьте файрвол (`ufw` на минимальных образах может быть не установлен, тогда порты открыты по умолчанию):

```bash
sudo ufw allow 80/tcp
sudo ufw allow 8091/tcp
sudo ufw status
```

### Вариант B. 80 и 443 свободны или заняты своим веб-сервером

Тогда TLS терминируем сами. Сертификат выпускать не нужно, если он уже есть на
машине; иначе — `certbot`. Скрипты добавляют виртуальные хосты
**443 → рабочий**, **8443 → тестовый**, редирект с HTTP и откатываются, если
конфигурация не проходит проверку.

**Apache:**

```bash
cd /opt/dnd-memory
sudo bash bin/apache-setup.sh 7f6a7dc97a7c.vps.myjino.ru
```

**nginx:**

```bash
cd /opt/dnd-memory
sudo bash bin/nginx-setup.sh 7f6a7dc97a7c.vps.myjino.ru
```

Сертификат выбирается только доверенный (проверка цепочки по системным корням) и
покрывающий домен — само-подписанный `snakeoil` не подойдёт, даже если его SAN
совпадает с именем хоста.

Проверка:

```bash
curl -s https://7f6a7dc97a7c.vps.myjino.ru/api/health
curl -s https://7f6a7dc97a7c.vps.myjino.ru:8443/api/health
```

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

Тестовый экземпляр доступен по `http://81.177.139.30:8091/` — на него и
настроена сборка APK, которую я приложил. Сертификат доверенный, поэтому никаких
послаблений для HTTP не требуется.

Если понадобится собрать самому:

```bash
cd android
./gradlew assembleDebug -PDND_SERVER_URL=http://81.177.139.30:8091/
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
| `bind: address already in use` | порт 80/8091 занят — поменяйте `APP_PORT` в `.env` или освободите порт |
| `502 Bad Gateway` на домене | экземпляр не запущен: `./prod/logs.sh` |
| `503 Service Unavailable` на домене | Apache не смог связаться с контейнером: `docker compose -p dnd-prod ps` |
| `✗ nginx не установлен` | на сервере Apache — запустите `sudo bash bin/apache-setup.sh <домен>` |
| контейнер падает с `password authentication failed` | в `.env` и в томе базы разные пароли: если меняли `DB_PASSWORD`, удалите том `docker compose -p dnd-prod down -v` (данные пропадут) |
| после перезагрузки ничего не поднялось | `sudo systemctl status docker dnd-memory` |
| не открывается `8091` | порт закрыт файрволом: `sudo ufw allow 8091/tcp` |
| `✗ порт 443 уже занят процессом «docker-proxy»` | 443 держит другой сервис (VPN): используйте вариант A, Apache на 443 не нужен |
| старая страница вместо приложения | конфликт `server_name` в nginx — см. предупреждение `nginx-setup.sh` |
