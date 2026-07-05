# Счётчик калорий

Простое Android-приложение для подсчёта калорий:

- дневная цель (по умолчанию 2000 ккал, меняется нажатием на «Цель»);
- добавление приёмов пищи (название + калории);
- прогресс дня: сколько съедено, сколько осталось / превышение;
- история по дням (стрелки ‹ › вверху экрана);
- удаление записи — долгое нажатие на неё;
- все данные хранятся локально на телефоне, интернет не нужен.

Минимальная версия Android — 8.0 (API 26).

## Как получить APK

### Способ 1 — GitHub Actions (без установки чего-либо)

В репозитории настроена автоматическая сборка (`.github/workflows/build-apk.yml`).
При каждом пуше GitHub сам собирает APK:

1. Откройте вкладку **Actions** в репозитории на GitHub.
2. Выберите последний запуск **Build APK** (зелёная галочка).
3. Внизу страницы, в разделе **Artifacts**, скачайте `calorie-counter-apk`.
4. Распакуйте zip — внутри `app-debug.apk`.
5. Перекиньте APK на телефон и откройте его. Android спросит разрешение
   на установку из неизвестных источников — разрешите.

Запустить сборку вручную: **Actions → Build APK → Run workflow**.

### Способ 2 — Android Studio

1. Установите [Android Studio](https://developer.android.com/studio).
2. **File → Open** — выберите папку проекта.
3. Дождитесь окончания синхронизации Gradle (первый раз качает зависимости).
4. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**.
5. Готовый файл: `app/build/outputs/apk/debug/app-debug.apk`.

### Способ 3 — командная строка

Нужны JDK 17+ и Android SDK (переменная `ANDROID_HOME` или файл
`local.properties` со строкой `sdk.dir=/путь/к/sdk`):

```bash
./gradlew assembleDebug        # Linux/macOS
gradlew.bat assembleDebug      # Windows
```

APK появится в `app/build/outputs/apk/debug/app-debug.apk`.

## Структура проекта

```
app/src/main/java/com/dyra/calories/
  MainActivity.kt   — экран приложения и вся логика UI
  Store.kt          — хранение записей и цели (SharedPreferences + JSON)
  Entry.kt          — модель записи (название, ккал, время)
  EntryAdapter.kt   — список записей (RecyclerView)
app/src/main/res/   — макеты, строки (русские), тема, иконка
```
