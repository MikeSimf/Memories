# DnD Memory

Справочник настольной сессии D&D: герои партии, встреченные персонажи, локации и
найденные предметы с краткими описаниями и живой навигацией по упоминаниям.
Пересказы сессий с черновиками, совместная работа с ролями и офлайн-режим на телефоне.

Проект состоит из трёх частей:

| Часть | Каталог | Технологии |
|---|---|---|
| Сервер приложений | [`server/`](server) | Java 11, Spring Boot 2.7, PostgreSQL 15, Flyway, JWT, Maven |
| Веб-версия (PWA) | [`web/`](web) | Vue 3, TypeScript, Vite, Pinia, IndexedDB, Service Worker |
| Android-приложение | [`android/`](android) | Java, Room, Retrofit, WorkManager, ZXing, MVVM |
| Развёртывание | [`docker/`](docker), [`scripts/`](scripts) | Docker Compose, nginx, два контура (test и prod) |

## Репозиторий

<https://github.com/MikeSimf/Memories> — ветка `main`.

## Что умеет

* **Справочник по вкладкам** — Герои, Персонажи, Локации, Предметы, Пересказы, Участники.
* **Живые ссылки в описаниях** — `[[Имя объекта]]` превращается в гиперссылку и ведёт
  на карточку; неразрешённые имена подсвечиваются пунктиром и предлагаются к созданию.
* **Поиск двух видов** — внутри активной вкладки и по всей кампании; работает и без сети.
* **Пересказы с состояниями** — черновик живёт только на устройстве и на сервер не
  уходит, публикация отправляет его в общий доступ; можно указать, от лица кого пересказ.
* **Роли участников** — владелец сессии, редактор как мастер, редактор как герой,
  просмотр как гость; у редактора-героя есть привязка к своему персонажу.
* **Приглашения по QR-коду** — владелец задаёт роль и связь с героем, гость сканирует
  код и присоединяется.
* **Вход по QR устройства** — мастер выпускает код для конкретного игрока, телефон
  сканирует его и получает годовую сессию: без пароля и без SMS. Почту, телефон и
  пароль человек добавляет позже в профиле — после этого может входить в веб-версию.
  Выданные устройства видны админу и отзываются одной кнопкой.
* **Офлайн-первый режим** — приложение работает без интернета, изменения копятся в
  очереди и синхронизируются при появлении сети, конфликты показываются пользователю.

## Документация

| Документ | О чём |
|---|---|
| [`docs/getting-started.md`](docs/getting-started.md) | Как забрать код и открыть его в IntelliJ IDEA, Android Studio, VS Code |
| [`docs/master-guide.md`](docs/master-guide.md) | Памятка мастера: роли, выдача доступа по QR, частые вопросы |
| [`docs/architecture.md`](docs/architecture.md) | Архитектура и принятые решения |
| [`docs/api.md`](docs/api.md) | HTTP API — контракт всех трёх частей |
| [`docs/sync.md`](docs/sync.md) | Протокол офлайн-синхронизации и разрешение конфликтов |
| [`docs/roles.md`](docs/roles.md) | Роли, права и жизненный цикл участника |
| [`docs/data-model.md`](docs/data-model.md) | Таблицы базы и связи между ними |
| [`docs/deployment.md`](docs/deployment.md) | Развёртывание на своём сервере (Docker, два контура, TLS) |
| [`docs/server-multi-project.md`](docs/server-multi-project.md) | Несколько проектов на одном сервере: шлюз, порты, имена |
| [`docs/api-examples.md`](docs/api-examples.md) | Ручная проверка API через curl |
| [`docs/verification.md`](docs/verification.md) | Что проверено запуском, а что нет |

## Быстрый старт

### Сервер локально

```bash
# PostgreSQL 15 нужен запущенным на localhost:5432, база dnd
cd server
mvn test                 # юнит-тесты без базы
mvn spring-boot:run      # http://localhost:8080/api/health
```

Переменные окружения — в [`server/src/main/resources/application.yml`](server/src/main/resources/application.yml):
`DND_DB_URL`, `DND_DB_USER`, `DND_DB_PASSWORD`, `DND_JWT_SECRET`, `DND_PUBLIC_URL`, `DND_CORS_ORIGINS`.

### Веб-версия

```bash
cd web
npm install
npm run dev              # разработка, ждёт сервер на /api
npm run build            # статика в web/dist
```

### Android

Открыть каталог `android/` в Android Studio (JDK 17, Android SDK 34) — подробности в
[`android/README.md`](android/README.md).

### Развёртывание на сервере

```bash
sudo bash scripts/install-docker.sh            # один раз: Docker и compose
sudo docker network create edge                # общая сеть для всех проектов
./scripts/ingress-up.sh                        # общий шлюз на 80/443
cp docker/env.test.example docker/env.test     # заполнить пароли и адрес
./scripts/deploy.sh test                       # тестовый контур, порт 8081
cp docker/env.prod.example docker/env.prod     # рабочий контур: домен
./scripts/deploy.sh prod
sudo certbot certonly --webroot -w docker/ingress/certbot-www -d ваш-домен
./scripts/ingress-domain.sh add ваш-домен dnd-prod-web 80
```

На сервере можно держать сколько угодно проектов: 80 и 443 занимает один общий шлюз,
рабочие контуры портов не публикуют, тестовые расходятся по портам 8081, 8082, …
Правила и реестр портов — в [`docs/server-multi-project.md`](docs/server-multi-project.md).

Подробная пошаговая инструкция, включая выпуск сертификата Let's Encrypt, резервные
копии и обновление версии, — в [`docs/deployment.md`](docs/deployment.md).

## Лицензия

Код написан для личного использования владельца проекта.
