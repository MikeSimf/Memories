# Как забрать код и открыть его в IDE

## 1. Откуда брать исходники

Важно понимать: репозиторий, о котором я писал, **локальный** — он лежит внутри моей
рабочей машины (`/data/external/dnd-memory`), у него **нет удалённого адреса**, поэтому
«склонировать по ссылке» его нельзя. Забрать код можно тремя способами.

### Способ 1. Скачать архив из чата (быстрее всего)

В чате под моим сообщением есть карточка файла **`dnd-memory-1.0.0.tar.gz`** — нажмите
на неё, файл скачается в загрузки. Это обычный gzip-архив, внутри весь проект
(сервер, веб, Android, Docker, документация) и готовые APK.

Распаковать:

```bash
# Linux / macOS
tar xzf dnd-memory-1.0.0.tar.gz

# Windows: 7-Zip или WinRAR (двойной клик по архиву)
# В PowerShell встроенный tar тоже умеет: tar xzf dnd-memory-1.0.0.tar.gz
```

### Способ 2. Положить проект в репозиторий аккаунта Chatium

Репозиторий аккаунта (`https://makeev-in.chatium.ru/s/git/makeev-in.git`) вы можете
клонировать где угодно, в том числе на своём сервере. Если положить проект в него
отдельной папкой `dnd-memory/`, получится так:

```bash
git clone https://makeev-in.chatium.ru/s/git/makeev-in.git
cd makeev-in/dnd-memory
```

Плюс: обновления от меня приезжают обычным `git pull`. Минус: в репозитории аккаунта
появится посторонний Java-проект, который Chatium собирает и публикует как сайт.

**Скажите слово — я это сделаю** (положу и запушу).

### Способ 3. Свой репозиторий на GitHub или GitLab (рекомендую для долгой работы)

Это правильный вариант, если проект будет жить: история коммитов, ветки, CI, доступ
с нескольких машин. Порядок такой:

1. Создайте **пустой** репозиторий на GitHub/GitLab (без README и .gitignore).
2. Пришлите мне ссылку и токен доступа (для GitHub — fine-grained token с правом
   `Contents: Read and write`), либо сделайте это сами, если у вас есть доступ к
   машине, где лежит архив:

```bash
cd dnd-memory
git remote add origin https://github.com/<вы>/dnd-memory.git
git branch -M main
git push -u origin main
```

3. Дальше на сервере и на рабочем компьютере — обычный `git clone`.

В проекте уже 8 коммитов с осмысленной историей — не потеряются.

## 2. Сервер приложений в IntelliJ IDEA

Нужен **JDK 11** (проект собран под Java 11; JDK 17 тоже подойдёт, но собирать лучше
11-м). Maven ставить не обязательно — в IDEA он встроенный.

1. **File → Open** → выберите файл `server/pom.xml` → **Open as Project**.
   Именно `pom.xml`, а не папку: тогда IDEA сразу поймёт, что это Maven-проект.
2. Дождитесь импорта (внизу справа прогресс «Resolving dependencies»).
3. **File → Project Structure → Project**: SDK = `11`, Language level = `11`.
   Если JDK 11 нет: **Add SDK → Download JDK → Temurin 11**.
4. Запуск: откройте `src/main/java/ru/dndmemory/DndMemoryApplication.java`,
   нажмите зелёный треугольник рядом с `main`.
5. Перед первым запуском задайте переменные окружения в конфигурации запуска
   (**Run → Edit Configurations → Environment variables**):

```
DND_DB_URL=jdbc:postgresql://localhost:5432/dnd?stringtype=unspecified
DND_DB_USER=dnd
DND_DB_PASSWORD=dnd
DND_JWT_SECRET=любая-строка-не-короче-32-символов
DND_FILES_DIR=/tmp/dnd-memory-files
```

Базу проще всего поднять одним контейнером (Docker Desktop):

```bash
docker run -d --name dnd-pg -p 5432:5432 \
  -e POSTGRES_DB=dnd -e POSTGRES_USER=dnd -e POSTGRES_PASSWORD=dnd postgres:15-alpine
```

Таблицы создаст Flyway при старте. Проверка: <http://localhost:8080/api/health>.

6. Тесты: правый клик на `src/test/java` → **Run 'All Tests'**. Три класса, 13 тестов,
   PostgreSQL для них не нужен.

**Полезно знать**

* Плагин **Lombok** обязателен (в IDEA он встроен, но включите
  `Settings → Build → Compiler → Annotation Processors → Enable annotation processing`),
  иначе IDEA будет «не видеть» геттеры и сеттеры.
* Файл `docs/api-examples.md` — готовый набор `curl`-запросов: удобно проверять API
  прямо из терминала IDEA (встроенный Terminal).
* Горячая перезагрузка: `Ctrl+F9` (Build) и перезапуск. Spring DevTools в проект
  сознательно не добавлен, чтобы не усложнять продакшен-сборку.

## 3. Android-приложение в Android Studio

Нужно: **Android Studio** (любая свежая, например Ladybug/Meerkat), **JDK 17**
(Android Studio ставит его сам), **Android SDK 34**.

1. **File → Open** → выберите папку `android` (именно папку, не файл).
2. Дождитесь **Gradle Sync**. Проект использует Gradle 8.5 (wrapper скачает сам) и
   Android Gradle Plugin 8.2.2.
3. Если студия попросит SDK 34: **Tools → SDK Manager** → отметьте
   `Android 14.0 (API 34)` и `Android SDK Build-Tools 34`.
4. Адрес сервера задаётся в файле `android/gradle.properties`:

```
DND_SERVER_URL=http://10.0.2.2:8080/
```

`10.0.2.2` — это «хост из эмулятора». Для телефона на том же Wi-Fi укажите адрес
компьютера в сети (`http://192.168.1.50:8080/`), для рабочего контура —
`https://ваш-домен/`. После правки: **Sync Project with Gradle Files**.
По умолчанию стоит `http://10.0.2.2:8080/` — под тестовый контур на 8081 замените на
`http://10.0.2.2:8081/`.

5. Запуск: выберите эмулятор или подключите телефон с включённой отладкой по USB,
   нажмите **Run ▶**.

**Полезно знать**

* Приложение работает офлайн: без сервера оно откроется и покажет ранее загруженные
  данные. Если при старте «пусто» — это ожидаемо, пока не задан доступный адрес сервера.
* HTTP (без `s`) разрешён только для отладочной сборки — в релизе нужен `https`.
  Исключения для локальных адресов описаны в `res/xml/network_security_config.xml`.
* Сборка из терминала (без студии):

```bash
cd android
./gradlew assembleDebug          # APK: app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug           # сразу на подключённый телефон
```

Если Gradle падает с «build daemon disappeared» — мало памяти, добавьте в
`gradle.properties` строку `org.gradle.jvmargs=-Xmx1024m` и запускайте с
`--no-daemon --max-workers=1`.

## 4. Веб-версия (VS Code, WebStorm или IDEA)

```bash
cd web
npm install
npm run dev      # http://localhost:5173, запросы /api уходят на localhost:8080
npm run build    # проверка типов + сборка статики в dist/
```

Адрес API для сборки задаётся переменной `VITE_API_BASE_URL` (файл `.env`), по
умолчанию запросы идут относительным путём `/api` — это правильный вариант для
работы за nginx в Docker.

## 5. Что где лежит

```
dnd-memory/
  server/     сервер приложений (Java 11, Spring Boot) — открывать в IntelliJ IDEA
  web/        веб-версия (Vue 3 + Vite)                — открывать в VS Code/WebStorm
  android/    Android-приложение (Java, Gradle)        — открывать в Android Studio
  docker/     Dockerfile-ы, compose тестового и рабочего контуров, общий шлюз
  scripts/    деплой, бэкап, проверки, упаковка
  docs/       документация: API, синхронизация, роли, развёртывание
  artifacts/  готовые APK (если есть в архиве)
```

Куда смотреть дальше: [`getting-started.md`](getting-started.md) — этот файл,
[`server-multi-project.md`](server-multi-project.md) — как разместить на одном сервере
несколько проектов, [`deployment.md`](deployment.md) — развёртывание по шагам,
[`verification.md`](verification.md) — что проверено, [`api.md`](api.md) — контракт API.
