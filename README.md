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
* **Офлайн-первый режим** — приложение работает без интернета, изменения копятся в
  очереди и синхронизируются при появлении сети, конфликты показываются пользователю.

## Документация

| Документ | О чём |
|---|---|
| [`docs/architecture.md`](docs/architecture.md) | Архитектура и принятые решения |
| [`docs/api.md`](docs/api.md) | HTTP API — контракт всех трёх частей |
| [`docs/sync.md`](docs/sync.md) | Протокол офлайн-синхронизации и разрешение конфликтов |
| [`docs/roles.md`](docs/roles.md) | Роли, права и жизненный цикл участника |
| [`docs/deployment.md`](docs/deployment.md) | Развёртывание на своём сервере (Docker, два контура) |

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
sudo bash scripts/install-docker.sh                       # один раз: Docker и compose
cp docker/env.test.example docker/env.test                # заполнить пароли и адрес
./scripts/deploy.sh test                                  # тестовый контур, порт 8081
cp docker/env.prod.example docker/env.prod                # рабочий контур: домен и TLS
./scripts/deploy.sh prod                                  # рабочий контур, 80/443
```

Подробная пошаговая инструкция, включая выпуск сертификата Let's Encrypt, резервные
копии и обновление версии, — в [`docs/deployment.md`](docs/deployment.md).

## Лицензия

Код написан для личного использования владельца проекта.
