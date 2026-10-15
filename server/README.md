# DnD Memory — сервер приложений

Java 11 + Spring Boot 2.7 + PostgreSQL 15. Отдаёт REST API для веб-версии и
Android-приложения, хранит справочник кампаний и ведёт журнал изменений для
офлайн-синхронизации.

## Сборка и запуск

```bash
# Юнит-тесты и проверка, что контекст приложения собирается (PostgreSQL не нужен)
mvn test

# Исполняемый jar -> target/dnd-memory-server.jar
mvn -DskipTests package

# Запуск (нужен PostgreSQL 15 с базой dnd)
export DND_DB_URL='jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified'
export DND_DB_USER=dnd
export DND_DB_PASSWORD=dnd
export DND_JWT_SECRET='случайная-строка-не-короче-32-символов'
java -jar target/dnd-memory-server.jar
```

Проверка: `curl http://localhost:8080/api/health` → `{"status":"UP", ...}`.

Требуется JDK 11+ и Maven 3.8+. Схема базы создаётся автоматически миграциями
Flyway при первом запуске.

## Тесты

| Тест | Что проверяет |
|---|---|
| `PermissionServiceTest` | Матрица прав ролей: владелец, мастер, редактор-герой, гость |
| `WikiLinkServiceTest` | Разбор ссылок `[[Имя]]`, `[[Имя\|подпись]]`, `[[#uuid\|подпись]]` |
| `ApplicationContextTest` | Весь Spring-контекст поднимается на H2: циклические зависимости, бины, привязка настроек |

`ApplicationContextTest` специально не требует PostgreSQL — он ловит ошибки
связывания бинов там, где нет базы. Настоящую проверку миграций и JPA удобно
делать так (нужен Docker):

```bash
./scripts/run-integration-tests.sh
```

Дополнительно есть сверка JPA-маппинга с SQL-схемой:

```bash
node scripts/check-schema.mjs
```

## Настройки (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DND_PORT` | `8080` | Порт HTTP |
| `DND_DB_URL` | `jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified` | Строка подключения. **Параметр `stringtype=unspecified` обязателен**: колонки `jsonb` пишутся из строк |
| `DND_DB_USER` / `DND_DB_PASSWORD` | `dnd` / `dnd` | Доступ к базе |
| `DND_JWT_SECRET` | демо-значение | Секрет подписи токенов, **не короче 32 байт**; в продакшене обязателен свой |
| `DND_ACCESS_TOKEN_MINUTES` | `15` | Время жизни access-токена |
| `DND_REFRESH_TOKEN_DAYS` | `30` | Время жизни refresh-токена |
| `DND_PUBLIC_URL` | `http://localhost:8080` | Внешний адрес: попадает в ссылки приглашений и QR-коды |
| `DND_CORS_ORIGINS` | `http://localhost:5173,http://localhost:8081` | Разрешённые источники (список через запятую) |
| `DND_FILES_DIR` | `/var/lib/dnd-memory/files` | Каталог загруженных файлов |
| `DND_LOG_LEVEL` | `INFO` | Уровень логирования `ru.dndmemory` |
| `DND_CHANGE_LOG_DAYS` / `DND_CHANGE_LOG_MAX_ROWS` | `90` / `50000` | Хранение журнала синхронизации |
| `DND_YANDEX_CLIENT_ID` / `DND_YANDEX_CLIENT_SECRET` | пусто | Вход через Яндекс ID |
| `DND_VK_CLIENT_ID` / `DND_VK_CLIENT_SECRET` | пусто | Вход через VK ID |

Пока внешние провайдеры не настроены, `GET /api/auth/providers` отдаёт
`enabled: false`, и интерфейсы показывают кнопки входа неактивными.

## Структура

```
src/main/java/ru/dndmemory/
  domain/     JPA-сущности и перечисления (User, Campaign, Member, Invite,
              GameEntity, Retelling, ChangeLog, SyncConflict, StoredFile)
  repo/       Spring Data репозитории
  service/    Бизнес-логика: AuthService, OAuthService, CampaignService,
              MemberService, InviteService, EntityService, RetellingService,
              SearchService, SyncService, WikiLinkService, ChangeRecorder,
              FileStorageService, DtoMapper, MaintenanceJob
  security/   JWT, права доступа (PermissionService), конфигурация Spring Security
  web/        REST-контроллеры, DTO, обработка ошибок
  config/     Настройки приложения, CORS, идентификатор запроса, RestTemplate
src/main/resources/
  application.yml
  db/migration/V1__init.sql    начальная схема
src/test/java/ru/dndmemory/    юнит-тесты
```

## Как устроены ключевые места

* **Одна таблица `entities` на все типы** (герой, персонаж, локация, предмет) —
  различия в JSON-колонке `attrs`. Поэтому ссылки, поиск и синхронизация работают
  одинаково для всех вкладок.
* **Ревизии кампании.** Каждое изменение увеличивает `campaigns.revision` и пишет
  строку в `change_log`. Строка кампании при этом блокируется
  (`SELECT ... FOR UPDATE`), поэтому ревизии выдаются строго по возрастанию даже при
  одновременных запросах.
* **Идемпотентность.** `change_log.client_change_id` уникален в пределах кампании:
  повторная отправка той же правки не создаёт вторую ревизию.
* **Разрешение ссылок** пересчитывается при каждом сохранении объекта: в ответе
  `links` содержит `targetId` для найденных имён и `resolved: false` для остальных.
* **Черновики пересказов** сервер не принимает: `status: "DRAFT"` → `422
  draft_not_synced`. Это требование заказчика — черновик не покидает устройство.
* **Файлы** лежат вне базы (`DND_FILES_DIR`), в базе только метаданные; в Docker это
  отдельный том.

Подробности — в [`../docs/architecture.md`](../docs/architecture.md),
[`../docs/api.md`](../docs/api.md), [`../docs/sync.md`](../docs/sync.md),
[`../docs/roles.md`](../docs/roles.md).
