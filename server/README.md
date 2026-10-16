# DnD Memory — сервер приложений

Java 11 + Spring Boot 2.7 + PostgreSQL 15. Отдаёт REST API для веб-версии и
Android-приложения, хранит справочник кампаний и ведёт журнал изменений для
офлайн-синхронизации.

После переработки v2 вход — только по логину и паролю, а объекты справочника живут
в четырёх своих таблицах (`heroes`, `characters`, `locations`, `items`) вместо общей
`entities`. Решения и мотивировка — в [`../docs/redesign-v2.md`](../docs/redesign-v2.md).

## Сборка и запуск

```bash
# Юнит-тесты и проверка, что контекст приложения собирается (PostgreSQL не нужен)
mvn test

# Исполняемый jar -> target/dnd-memory-server.jar
mvn -DskipTests package

# Сверка JPA-маппинга с SQL-схемой (запускать из корня репозитория)
node scripts/check-schema.mjs

# Запуск (нужен PostgreSQL 15 с базой dnd)
export DND_DB_URL='jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified'
export DND_DB_USER=dnd
export DND_DB_PASSWORD=dnd
export DND_JWT_SECRET='случайная-строка-не-короче-32-символов'
java -jar target/dnd-memory-server.jar
```

Проверка: `curl http://localhost:8080/api/health` → `{"status":"UP", ...}`.

## Запуск на сервере без Docker

Готовый шаблон настроек — [`application.example.yml`](application.example.yml), юнит
systemd — [`scripts/dnd-memory.service`](../scripts/dnd-memory.service), пошаговая
инструкция — [`docs/run-without-docker.md`](../docs/run-without-docker.md).

## Если у вас рабочее зеркало Maven

Если в `~/.m2/settings.xml` прописано рабочее зеркало (обычно с `<mirrorOf>*</mirrorOf>`),
оно перехватывает **все** репозитории, и проект может не собраться. Менять рабочие
настройки не нужно — в проекте есть свои:

* `server/.mvn/settings.xml` — только Maven Central, без зеркал;
* `server/.mvn/maven.config` — подключает этот файл автоматически, поэтому **обычный
  `mvn` из каталога `server/` уже собирает проект** и ваши рабочие настройки для
  остальных проектов не затрагиваются;
* `scripts/build-server.sh` — то же самое, если запускаете из корня репозитория или
  из другого каталога:

```bash
./scripts/build-server.sh                 # mvn package
./scripts/build-server.sh test            # тесты
./scripts/build-server.sh clean package   # любые цели Maven
```

Проверено: проектные настройки перекрывают зеркало даже тогда, когда оно прописано в
глобальном `conf/settings.xml` самого Maven (а не только в пользовательском).

Требуется JDK 11+ и Maven 3.8+. Схема базы создаётся автоматически миграциями
Flyway при первом запуске.

## Тесты

`mvn test` — 29 тестов, PostgreSQL не нужен (H2 в режиме совместимости, Flyway
выключен).

| Тест | Что проверяет |
|---|---|
| `PermissionServiceTest` | Матрица прав ролей: владелец, мастер, редактор-герой, гость |
| `ObjectMatchKeyTest` | Ключ сопоставления классов: `Hero`/`Location`/`Item` — имя, `Character` — имя и раса |
| `AuthServiceRegisterTest` | Регистрация по логину: нижний регистр, приглашение, срок сессии по платформе |
| `InviteServiceTest` | Приглашение одноразовое: принятие гасит его, повтор и просрочка отклоняются |
| `SecurityRulesTest` | Что открыто без токена и что закрыто; удалённые эндпоинты OAuth и `/entities` |
| `WikiLinkServiceTest` | Разбор ссылок `[[Имя]]`, `[[Имя\|подпись]]`, `[[#uuid\|подпись]]` |
| `PhoneUtilsTest` | Приведение телефона к `+7XXXXXXXXXX` |
| `ApplicationContextTest` | Весь Spring-контекст поднимается на H2: циклические зависимости, бины, привязка настроек |

`ApplicationContextTest` специально не требует PostgreSQL — он ловит ошибки
связывания бинов там, где нет базы. Настоящую проверку миграций и JPA удобно
делать так (нужен Docker):

```bash
./scripts/run-integration-tests.sh
```

`check-schema.mjs` читает миграции по порядку (учитывая `DROP TABLE`,
`DROP COLUMN` и `RENAME COLUMN`) и собирает колонки листовых `@Entity` вместе с
базовым `@MappedSuperclass` — иначе общие поля четырёх классов выглядели бы как
незамапленные.

## Настройки (переменные окружения)

| Переменная | По умолчанию | Назначение |
|---|---|---|
| `DND_PORT` | `8080` | Порт HTTP |
| `DND_DB_URL` | `jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified` | Строка подключения. **Параметр `stringtype=unspecified` обязателен**: колонки `jsonb` пишутся из строк |
| `DND_DB_USER` / `DND_DB_PASSWORD` | `dnd` / `dnd` | Доступ к базе |
| `DND_JWT_SECRET` | демо-значение | Секрет подписи токенов, **не короче 32 байт**; им же подписываются токены сброса пароля, в продакшене обязателен свой |
| `DND_ACCESS_TOKEN_MINUTES` | `15` | Время жизни access-токена |
| `DND_REFRESH_TOKEN_DAYS` | `30` | Время жизни refresh-токена веб-версии |
| `DND_ANDROID_REFRESH_TOKEN_DAYS` | `365` | Время жизни refresh-токена приложения (`platform: "android"`) |
| `DND_ADMIN_TOKEN` | пусто | Токен админ-панели `GET /api/admin/password-resets`. **Если не задан, эндпоинт отвечает `404`** |
| `DND_PUBLIC_URL` | `http://localhost:8080` | Внешний адрес: попадает в ссылки приглашений, QR-коды и ссылку сброса пароля |
| `DND_CORS_ORIGINS` | `http://localhost:5173,http://localhost:8081` | Разрешённые источники (список через запятую) |
| `DND_FILES_DIR` | `/var/lib/dnd-memory/files` | Каталог загруженных файлов |
| `DND_LOG_LEVEL` | `INFO` | Уровень логирования `ru.dndmemory` |
| `DND_CHANGE_LOG_DAYS` / `DND_CHANGE_LOG_MAX_ROWS` | `90` / `50000` | Хранение журнала синхронизации |

Почтовый сервер не настроен, поэтому письма о сбросе пароля не отправляются:
одноразовая ссылка пишется в лог строкой
`СБРОС ПАРОЛЯ: login=… email=… ссылка=<DND_PUBLIC_URL>/#/reset/<token>`, а ожидающие
запросы видны в админ-панели по `X-Admin-Token`.

## Структура

```
src/main/java/ru/dndmemory/
  domain/     JPA-сущности и перечисления: экстендеры-интерфейсы в domain/extender
              (GameEntityExtender, NamedExtender, SyncExtended, ImageExtender),
              база GameEntitySupport (@MappedSuperclass, общее состояние),
              листовые Hero, Character, Location, Item, HeroItem, а также
              User, Campaign, Member, Invite, RefreshToken, PasswordResetRequest,
              Retelling, ChangeLog, SyncConflict, StoredFile, ObjectType
  repo/       Spring Data репозитории: общий GameEntityRepository<T> и четыре
              конкретных, плюс HeroItemRepository, PasswordResetRepository
  service/    Бизнес-логика: AbstractObjectService и четыре сервиса классов
              (HeroService, CharacterService, LocationService, ItemService),
              ObjectIndexService (ссылки и имена), AuthService, PasswordResetService,
              TokenUtils, CampaignService, MemberService, InviteService,
              RetellingService, SearchService, SyncService, WikiLinkService,
              ChangeRecorder, ConflictRecorder, FileStorageService, DtoMapper,
              MaintenanceJob
  security/   JWT, права доступа (PermissionService), конфигурация Spring Security
  web/        REST-контроллеры (AuthController, AdminController, четыре контроллера
              классов, CampaignController, MemberController, InviteController,
              RetellingController, SearchController, SyncController, FileController),
              DTO, обработка ошибок
  config/     Настройки приложения, CORS, идентификатор запроса
src/main/resources/
  application.yml
  db/migration/V1__init.sql                 начальная схема
  db/migration/V2__devices_and_contacts.sql контакты и устройства (история)
  db/migration/V3__classes_and_login.sql    логин, четыре класса, сброс пароля
src/test/java/ru/dndmemory/    юнит-тесты
```

## Как устроены ключевые места

* **Четыре класса вместо одной таблицы.** Контракты разделены на независимые
  интерфейсы-экстендеры (`GameEntityExtender`, `NamedExtender`, `SyncExtended`,
  `ImageExtender` — ни один не наследует другой), а общее состояние (`id`,
  `campaignId`, `name`, `description`, `imageFileId`, `deleted`, `createdAt`,
  `updatedAt`, `createdBy`, `updatedBy`, `revision`) объявлено один раз в
  `@MappedSuperclass GameEntitySupport`, который эти интерфейсы реализует: Java не
  позволяет наследовать поля от нескольких классов. Листовые классы — обычные
  `@Entity` со своей таблицей и явным списком реализуемых интерфейсов, поэтому новое
  поле типа не затрагивает остальные, а общая логика REST и синхронизации живёт в
  `AbstractObjectService`.
* **Ключ сопоставления** — метод самого класса (`Hero.matchKey()` и т.д.), по нему
  синхронизация находит уже существующий объект вместо создания дубля: `Hero`,
  `Location` и `Item` — по имени, `Character` — по имени и расе.
* **Ревизии кампании.** Каждое изменение увеличивает `campaigns.revision` и пишет
  строку в `change_log`. Строка кампании при этом блокируется
  (`SELECT ... FOR UPDATE`), поэтому ревизии выдаются строго по возрастанию даже при
  одновременных запросах.
* **Идемпотентность.** `change_log.client_change_id` уникален в пределах кампании:
  повторная отправка той же правки не создаёт вторую ревизию.
* **Разрешение ссылок** пересчитывается при каждом сохранении: в ответе `links`
  содержит `targetId` для найденных имён и `resolved: false` для остальных. Имена
  ищутся по всем четырём классам кампании.
* **Сброс пароля без SMTP.** `password_reset_requests` хранит только хеш токена;
  сам токен выводится из `DND_JWT_SECRET`, пользователя и времени запроса
  (`TokenUtils.passwordResetToken`), поэтому его можно показать администратору, не
  храня в базе.
* **Черновики пересказов** сервер не принимает: `status: "DRAFT"` → `422
  draft_not_synced`. Это требование заказчика — черновик не покидает устройство.
* **Файлы** лежат вне базы (`DND_FILES_DIR`), в базе только метаданные; в Docker это
  отдельный том.

Подробности — в [`../docs/architecture.md`](../docs/architecture.md),
[`../docs/api.md`](../docs/api.md), [`../docs/sync.md`](../docs/sync.md),
[`../docs/roles.md`](../docs/roles.md).
