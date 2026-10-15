# DnD Memory — Android-приложение

Справочник настольной сессии D&D: герои партии, встреченные персонажи, локации,
предметы, пересказы сессий и живая навигация по упоминаниям (`[[Имя]]` — ссылка на
карточку объекта). Приложение офлайн-первое: интерфейс работает по локальной базе,
сеть нужна только для синхронизации.

Контракт с сервером: `../docs/api.md`, `../docs/sync.md`, `../docs/roles.md`,
раздел 5 `../docs/architecture.md`.

## 1. Как открыть и собрать

```bash
# Android Studio: File → Open → выбрать каталог android/
# либо из командной строки:
./gradlew :app:assembleDebug
```

Требования:

| Что | Версия |
|---|---|
| Android Studio | Ladybug (2024.2) или новее |
| JDK | 17 (Android Studio использует встроенный JBR 17) |
| Android Gradle Plugin | 8.2.2 |
| Gradle | 8.5 (wrapper в комплекте) |
| compileSdk / targetSdk | 34 |
| minSdk | 24 |
| Build Tools | 34.0.0 (ставится Android Studio) |

Исходный код — **только Java 11**, без Kotlin, Hilt и Dagger (всё внедрение
зависимостей — вручную в `AppContainer`), без `kapt`/`annotationProcessor` кроме
Room.

JDK 17 нужен для запуска AGP 8.2; уровень языка задан как Java 11
(`compileOptions { sourceCompatibility JavaVersion.VERSION_11 }`), поэтому
приложение совместимо с Java 11.

## 2. Адрес сервера

Базовый адрес берётся из `BuildConfig.DND_SERVER_URL` и задаётся в
`gradle.properties`:

```properties
DND_SERVER_URL=http://10.0.2.2:8080/
```

* `10.0.2.2` — адрес хост-машины из эмулятора Android, то есть тестовый или
  рабочий контур, поднятый на том же компьютере.
* Для реального устройства укажите адрес контура, например
  `DND_SERVER_URL=https://dnd.example.com/`.
* Значение обязано заканчиваться на `/` (скрипт добавит его сам, если забыли).
* Открытый HTTP разрешён только для `10.0.2.2`, `localhost` и `127.0.0.1`
  (`app/src/main/res/xml/network_security_config.xml`). Если сервер развёрнут по
  `http://` на другом адресе, добавьте его в этот файл; для `https://` ничего
  менять не нужно.

Другие поля `BuildConfig`: `OAUTH_REDIRECT_URI` (`dndmemory://oauth`) и
`DEEP_LINK_SCHEME` (`dndmemory`).

## 3. Структура проекта

```
android/
├── settings.gradle, build.gradle, gradle.properties, gradlew, gradlew.bat
├── gradle/wrapper/                        # Gradle 8.5
└── app/
    ├── build.gradle, proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml
        ├── java/ru/dndmemory/
        │   ├── App.java                   # Application: собирает контейнер, стартует синхронизацию
        │   ├── AppContainer.java           # ручное DI: Room, OkHttp/Retrofit, репозитории, SyncEngine
        │   ├── data/
        │   │   ├── Mappers.java            # сеть ↔ Room (единственное место сопоставления)
        │   │   ├── model/                  # EntityType, MemberRole, EntityLink, SyncResult, …
        │   │   ├── local/                  # Room: сущности, DAO, конвертеры, AppDatabase
        │   │   │   ├── SessionStore.java   # токены и clientId установки
        │   │   │   └── SettingsStore.java  # «синхронизировать только по Wi-Fi»
        │   │   ├── remote/                 # ApiService (все эндпоинты api.md), DTO, перехватчики
        │   │   └── repo/                   # 6 репозиториев + PendingWriter + LinkResolver
        │   ├── sync/                       # SyncEngine, SyncWorker, SyncScheduler
        │   ├── ui/                         # MVVM: Activity/Fragment → ViewModel → LiveData
        │   │   ├── splash, auth            # вход, регистрация, OAuth через Custom Tabs
        │   │   ├── campaigns, campaign     # список кампаний; кампания с 6 вкладками
        │   │   ├── entity                  # список, карточка, редактор по типам (attrs)
        │   │   ├── retelling               # список, чтение, редактор, черновик → публикация
        │   │   ├── members                 # роли и привязка к герою
        │   │   ├── invite                  # создание, QR, список, отзыв, сканирование, принятие
        │   │   ├── search                  # поиск по вкладке и общий (по локальной базе)
        │   │   ├── settings                # синхронизация, полная перезагрузка, выход
        │   │   └── common                  # Labels, Attrs, Rights, Nav, NameIndex, ViewModelFactory
        │   └── util/                       # WikiMarkdown, DateFormats, PermissionHelper, QrUtils
        └── res/                            # layout, menu, strings (русский), themes (светлая/тёмная),
                                            # drawable (векторные иконки), mipmap (иконка), xml
```

## 4. Офлайн-режим

Локальная база (Room) — единственный источник данных для интерфейса. Сеть меняет
её двумя путями: прямыми запросами (список кампаний, участники, приглашения,
справочные выборки) и движком синхронизации `SyncEngine`.

Правки объектов и пересказов всегда локальные:

1. запись сразу попадает в Room, объект помечается `dirty = 1`;
2. в `pending_changes` добавляется запись с `base_revision` объекта;
3. `SyncScheduler` ставит отложенный воркер (debounce 3 с).

`SyncEngine` выполняет цикл из `docs/sync.md` строго в порядке **push → pull**:

* `POST /api/campaigns/{id}/sync/changes` — очередь уходит по возрастанию `seq`,
  каждая правка несёт `clientChangeId` (ключ идемпотентности);
* `APPLIED` / `CONFLICT_RESOLVED` — правка снимается с очереди, объект помечается
  чистым; `REJECTED` — снимается с очереди, состояние сервера сохраняется в
  `conflict_json`, интерфейс показывает «есть расхождение» и предлагает выбрать
  свою версию или серверную;
* `GET /api/campaigns/{id}/sync/changes?since=…` — журнал применяется к базе,
  `lastPulledRevision = latestRevision`, при `hasMore = true` запрос повторяется;
* `410 resync_required` (журнал скомпактился) — полная перезагрузка через
  `GET /sync/snapshot` с курсорами.

Черновики пересказов **не покидают устройство**: пока пользователь не нажал
«Опубликовать», текст живёт в таблице `retelling_drafts` и не попадает ни в
`pending_changes`, ни в push. Публикация переносит его в `retellings` со
`status = PUBLISHED` и ставит правку в очередь.

Ссылки `[[Имя]]`, `[[Имя|подпись]]`, `[[#uuid|подпись]]` разрешаются **по
локальной базе** (те же правила, что у сервера): разрешённые подчёркнуты и
открывают карточку, неразрешённые нарисованы пунктиром и предлагают создать
объект.

Когда запускается синхронизация:

| Событие | Реализация |
|---|---|
| Старт приложения | `App.onCreate` → `SyncEngine.syncAll()` в фоне |
| Появление сети | `WorkManager`, `NetworkType.CONNECTED` |
| Локальная правка | отложенный воркер, debounce 3 с |
| Вручную | pull-to-refresh в списках, «Синхронизировать сейчас» в настройках |
| Периодически | периодический воркер раз в 15 минут |

Статус синхронизации виден в шапке списка кампаний и экрана кампании:
«Синхронизировано», «Синхронизация…», «Нет сети — работаем офлайн»,
«Ошибка синхронизации», «Есть расхождения с сервером».

## 5. Роли и права

Права приходят от сервера в `myPermissions` и проверяются на клиенте только для
того, чтобы не показывать недоступные действия (`ui/common/Rights.java`,
матрица — `../docs/roles.md`). Решение всё равно принимает сервер.

## 6. Вход и приглашения

* Почта и пароль: `POST /api/auth/register`, `POST /api/auth/login`.
* Яндекс ID / VK ID: страница провайдера открывается во вкладке Custom Tabs,
  возврат — по deep link `dndmemory://oauth` (`OAuthCallbackActivity`); значение
  `state` хранится в памяти процесса, подмена ответа отклоняется.
* Обновление access-токена — `TokenAuthenticator`: на `401` один раз вызывается
  `POST /api/auth/refresh` (refresh-токен ротируется), запрос повторяется. При
  сетевой ошибке сессия **не** сбрасывается, иначе офлайн-режим ломался бы.
* Приглашения: ссылка `dndmemory://join?token=…` открывает
  `InviteAcceptActivity` — предпросмотр публичный, а принятие требует входа
  (после авторизации приложение возвращает пользователя на тот же экран).
  QR-код сканируется камерой (ZXing embedded) либо открывается ссылкой
  `deepLink` из ответа сервера.

## 7. Тестовый обход

1. Запустить сервер (контур `test`, порт 8081) и указать его в `DND_SERVER_URL`.
2. Собрать и установить приложение, зарегистрироваться.
3. Создать кампанию, добавить героя и НПС, связать их ссылкой `[[Имя]]`.
4. Включить авиарежим, поправить объект и создать пересказ-черновик — интерфейс
   продолжает работать, в шапке «Нет сети — работаем офлайн».
5. Выключить авиарежим, дождаться синхронизации (или pull-to-refresh) — правки
   уходят на сервер, черновик остаётся на устройстве до нажатия «Опубликовать».

## 8. Известные отклонения и точки внимания

* **Хранилище токенов.** В `app/build.gradle` объявлена зависимость
  `androidx.datastore:datastore-preferences:1.0.0`, но `SessionStore` использует
  закрытый файл `SharedPreferences`. Причина: Java-API DataStore работает только
  через coroutine-interop (`BuildersKt.runBlocking`, `FlowKt.first`), который
  нельзя проверить без компилятора, а доступ к токену нужен синхронно в
  `AuthInterceptor`. Перевести хранилище на DataStore можно, заменив реализацию
  одного класса: интерфейс `SessionStore` изолирует это решение.
* **Загрузка изображений в офлайне** не реализована — как и указано в
  `docs/architecture.md`, раздел 7. Эндпоинт `POST /api/files` объявлен в
  `ApiService`, но UI прикрепления картинки в первой версии нет.
* **Push-уведомлений нет** (осознанно, `docs/architecture.md`, раздел 7).
* `gradle/wrapper/gradle-wrapper.jar` — настоящий jar обёртки Gradle 8.5
  (проверен по центральному каталогу zip). Если политика безопасности не
  пропускает бинарные файлы, восстановите его командой `gradle wrapper` или
  скачиванием из дистрибутива Gradle 8.5.

## 9. Проверка сборки

Сборка прогнана по-настоящему (JDK 17, Android SDK 34, Gradle 8.5 из wrapper):

```bash
cd android
export JAVA_HOME=$(ls -d /data/external/tools/jdk-17* | head -1)
export ANDROID_HOME=/data/external/tools/android-sdk
export GRADLE_USER_HOME=/data/external/tools/gradle-home

./gradlew assembleDebug --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m" -Dorg.gradle.parallel=false

./gradlew assembleRelease --no-daemon --max-workers=1 \
  -Dorg.gradle.jvmargs="-Xmx1024m -XX:MaxMetaspaceSize=512m" -Dorg.gradle.parallel=false
```

Результат: `BUILD SUCCESSFUL` для обоих вариантов.

| Артефакт | Размер | Примечание |
|---|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 8,1 МБ | без минификации |
| `app/build/outputs/apk/release/app-release-unsigned.apk` | 3,0 МБ | R8 + `shrinkResources`, подпись не настроена |

Дополнительно проверено по собранным APK (`aapt2 dump badging` / `xmltree`, `dexdump`):

* `package ru.dndmemory`, `minSdk 24`, `targetSdk 34`, версия 1.0.0;
* разрешения: `INTERNET`, `ACCESS_NETWORK_STATE`, `CAMERA`
  (`WAKE_LOCK`, `RECEIVE_BOOT_COMPLETED`, `FOREGROUND_SERVICE` добавляет WorkManager);
* все 15 активностей попали в release-манифест, launcher — `SplashActivity`;
* оба deep link на месте: `dndmemory://oauth` и `dndmemory://join`;
* адаптивная иконка (`mipmap-anydpi-v26/ic_launcher.xml`) собирается, есть PNG-фолбэки
  для API 24–25;
* правила R8 сохраняют имена полей DTO: в release-DEX у `EntityDto` остались
  `campaignId`, `clientId`, `summary`, `description`, `attrs`, `tags`, `links`,
  `revision`, `createdAt`, `updatedAt` и остальные — Gson-сериализация не сломается.

Для установки на устройство соберите release с ключом:

```bash
./gradlew assembleRelease -Pandroid.injected.signing.store.file=... # или настройте signingConfigs
```
