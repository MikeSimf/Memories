# Несколько проектов на одном сервере

На сервере будут жить рабочие и тестовые версии разных проектов. Чтобы они не
мешали друг другу, приняты три правила: **один общий шлюз на 80/443**, **тестовые
контуры на своих портах**, **базы наружу не публикуются**.

## 1. Схема

```
                        Интернет
                            │
              ┌─────────────┴─────────────┐
              │  :80 / :443               │
              │  ingress-nginx            │  ← один на весь сервер,
              │  (docker/ingress)         │    владеет сертификатами и доменами
              └─────────────┬─────────────┘
                            │ общая сеть Docker «edge»
      ┌─────────────────────┼─────────────────────┬───────────────────┐
      │                     │                     │                   │
 dnd-prod-web         dnd-test-web           app2-prod-web       app3-prod-web
 (домен dnd.…)        (:8081 напрямую)       (домен app2.…)      (домен app3.…)
      │                     │                     │                   │
 dnd-prod-server      dnd-test-server        app2-prod-server    app3-prod-server
      │                     │                     │                   │
 dnd-prod-db          dnd-test-db            app2-prod-db        app3-prod-db
```

* **Шлюз** — единственный, кто слушает 80 и 443. Он решает по домену, в какой
  контейнер отправить запрос, и держит сертификаты Let's Encrypt.
* **Рабочие контуры** вообще не публикуют порты: их видно только внутри сети `edge`.
* **Тестовые контуры** публикуют свой порт (`8081`, `8082`, …) — так вы открываете их
  с телефона или ноутбука без возни с DNS и сертификатами.
* **Базы данных** не публикуются никогда: у каждой свой том Docker и своя сеть
  внутри compose-проекта.

## 2. Реестр портов и имён

Договоримся держать это в одном месте — добавьте строку, когда появляется проект.

| Порт | Назначение | Кто занимает |
|---|---|---|
| 22 | SSH | система |
| 80, 443 | HTTP/HTTPS | **общий шлюз** (`ingress-nginx`) |
| 8080 | зарезервирован под служебное (мониторинг, админка) | свободен |
| 8081 | тестовый контур DnD Memory | `dnd-test-web` |
| 8082 | тестовый контур следующего проекта | `app2-test-web` |
| 8083–8099 | следующие тестовые контуры | — |
| 5432, 6379, 27017… | **не публикуем** — базы живут внутри compose-сетей | — |

| Проект | Контур | Домен | Порт | Compose-проект | Контейнеры |
|---|---|---|---|---|---|
| DnD Memory | prod | `dnd.example.com` | 80/443 через шлюз | `dnd-prod` | `dnd-prod-{web,server,db}` |
| DnD Memory | test | `test.dnd.example.com` (необязательно) | 8081 | `dnd-test` | `dnd-test-{web,server,db}` |
| *следующий* | prod | `app2.example.com` | через шлюз | `app2-prod` | `app2-prod-{web,server,db}` |
| *следующий* | test | — | 8082 | `app2-test` | `app2-test-{web,server,db}` |

Соглашение об именах, которое делает всё предсказуемым:

* каталог проекта — `/opt/apps/<проект>`;
* compose-проект — `<проект>-<контур>` (`-p dnd-prod`);
* контейнеры — `<проект>-<контур>-<роль>`;
* тома — `<проект>-<контур>_<имя>` (Docker добавляет имя проекта к тому автоматически);
* порт тестового контура — закреплён за проектом в реестре выше.

## 3. Подготовка сервера (один раз)

```bash
# 1. Docker и compose
sudo bash /opt/apps/dnd-memory/scripts/install-docker.sh

# 2. Пользователь для работы без sudo (по желанию)
sudo usermod -aG docker $USER && newgrp docker

# 3. Общая сеть для шлюза
sudo docker network create edge

# 4. Каталог под проекты
sudo mkdir -p /opt/apps && sudo chown -R $USER:$USER /opt/apps

# 5. Шлюз
cd /opt/apps/dnd-memory
./scripts/ingress-up.sh
```

Проверка: `docker ps` покажет `ingress-nginx`, порты `0.0.0.0:80->80` и `443->443`.

## 4. Развернуть проект

```bash
cd /opt/apps/dnd-memory

# окружение тестового контура
cp docker/env.test.example docker/env.test
nano docker/env.test          # DB_PASSWORD, JWT_SECRET (≥32 символа), PUBLIC_URL, CORS_ORIGINS, TEST_PORT
./scripts/deploy.sh test      # поднимет dnd-test-{db,server,web}, тест будет на :8081

# окружение рабочего контура
cp docker/env.prod.example docker/env.prod
nano docker/env.prod          # DOMAIN, CORS_ORIGINS, DB_PASSWORD, JWT_SECRET
./scripts/deploy.sh prod      # поднимет dnd-prod-{db,server,web} без публикации портов
```

## 5. Подключить домен к шлюзу

```bash
cd /opt/apps/dnd-memory

# 1. Сертификат (шлюз уже обслуживает /.well-known/acme-challenge/)
sudo certbot certonly --webroot -w /opt/apps/dnd-memory/docker/ingress/certbot-www -d dnd.example.com

# 2. Прописать домен в шлюз
./scripts/ingress-domain.sh add dnd.example.com dnd-prod-web 80

# 3. Проверить
./scripts/ingress-domain.sh list
curl -I https://dnd.example.com
```

Скрипт сам понимает, выпущен ли сертификат: без него сделает конфигурацию только для
HTTP (чтобы `certbot` мог пройти проверку), с ним — полную с редиректом на HTTPS.
Поэтому порядок может быть любым: добавили домен → выпустили сертификат → повторили
`add`, и он переключится на HTTPS.

Тестовый контур тоже можно повесить на домен:

```bash
./scripts/ingress-domain.sh add test.dnd.example.com dnd-test-web 80
```

## 6. Как добавить следующий проект

1. Займите порт из реестра (следующий свободный после 8081 — `8082`).
2. Скопируйте проект в `/opt/apps/<проект>` (git clone или архив).
3. Возьмите за образец `docker/compose.prod.yml` и `docker/compose.test.yml` из
   DnD Memory. Что изменить:
   * `name:` — на `<проект>-prod` / `<проект>-test`;
   * `container_name:` — на `<проект>-prod-web` и т. д.;
   * `DND_*` переменные — на свои;
   * в тестовом контуре порт `"${TEST_PORT:-8082}:80"`.
4. **Не меняйте** два места: сеть `edge` (внешняя, общая) у сервиса `web` — иначе
   шлюз не увидит контейнер; отсутствие `ports:` в рабочем контуре.
5. Поднимите контур и добавьте домен:

```bash
cd /opt/apps/app2
./scripts/deploy.sh prod
cd /opt/apps/dnd-memory        # шлюз один, управляется из любого проекта
./scripts/ingress-domain.sh add app2.example.com app2-prod-web 80
```

## 7. Бэкапы и обновления

```bash
# Бэкап (в каждом проекте свой скрипт)
cd /opt/apps/dnd-memory && ./scripts/backup.sh prod

# Обновление одного контура, не трогая остальные
cd /opt/apps/dnd-memory && git pull && ./scripts/deploy.sh prod

# Обновить только шлюз (например, после правки конфигурации nginx)
cd /opt/apps/dnd-memory && ./scripts/ingress-up.sh

# Состояние всех контейнеров сервера разом
docker ps --format 'table {{.Names}}\t{{.Status}}\t{{.Ports}}'
```

В cron удобно поставить общий бэкап всех проектов:

```cron
0 4 * * * cd /opt/apps/dnd-memory && ./scripts/backup.sh prod >> backups/backup.log 2>&1
30 4 * * * cd /opt/apps/app2 && ./scripts/backup.sh prod >> backups/backup.log 2>&1
```

## 8. Что может пойти не так

| Симптом | Причина и решение |
|---|---|
| `network edge declared as external, but could not be found` | сеть не создана: `docker network create edge` |
| Домен открывается, но `502` | контейнер `*-web` не в сети `edge` или ещё не поднялся: `docker compose -p dnd-prod logs web` |
| `bind: address already in use` на 443 | кто-то ещё слушает 443 (например, системный nginx): `sudo ss -ltnp \| grep :443`, остановите лишнее |
| После `add` сайт не открывается | конфигурация не применилась: `./scripts/ingress-domain.sh reload` |
| `nginx: [emerg] cannot load certificate` | в конфигурацию попал домен без сертификата: уберите файл `docker/ingress/conf.d/<домен>.conf` и повторите `add` после выпуска сертификата |
| Тестовый контур на 8081 недоступен снаружи | порт закрыт файрволом: `sudo ufw allow 8081/tcp` |
