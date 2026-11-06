# Dairy (Voice Habit & Task Tracker) — Notion AI Agent Workspace & Handoff

> **Этот документ создан специально для облачного Notion AI Агента.**  
> Он содержит полный контекст проекта, карту кодовой базы, архитектурные стандарты и бэклог задач.  
> Поскольку агент работает в облаке Notion, все ресурсы и исходный код доступны по прямым ссылкам ниже.

---

## 🌐 Облачные ресурсы проекта

- **Главный репозиторий GitHub:** [DmKOwO/voice-habit-tracker](https://github.com/DmKOwO/voice-habit-tracker)
- **Скачать полный исходный код (ZIP):** [`main.zip`](https://github.com/DmKOwO/voice-habit-tracker/archive/refs/heads/main.zip)
- **Готовые релизы и Android APK:** [GitHub Releases](https://github.com/DmKOwO/voice-habit-tracker/releases)
- **Текущая версия приложения:** `v1.4.3`

---

## 📱 О проекте Dairy

**Dairy** — минималистичный Android-дневник мыслей, трекер привычек и органайзер задач с локальным хранением данных (Local-first) и глубокой интеграцией голосового ввода.
Пользователь наговаривает свободный поток мыслей в микрофон, а система классифицирует намерение, выделяет задачи, фиксирует выполнение привычек и формирует структурированный конспект.

### Архитектурный стек
1. **Android (нативное приложение):**
   - **Язык & UI:** Kotlin, Jetpack Compose, Material 3, кастомная дизайн-система `Duro`.
   - **Архитектура:** Clean Architecture (Presentation, Domain, Data, Core), MVI/MVVM, единый `AppContainer` (DI).
   - **Данные:** Room DB (SQLite) с миграциями, `Flow`, реактивные репозитории.
   - **Фоновые процессы:** Kotlin Coroutines, WorkManager (очередь распознавания при плохой связи `VoiceUploadWorker`).
   - **Интеграция:** Экспорт в Obsidian через Storage Access Framework (SAF).
2. **Backend (микросервис обработки речи):**
   - Python 3.11+, FastAPI, Uvicorn, Pydantic v2.
   - Распознавание речи (STT): Groq Whisper Large v3 Turbo (быстрый отклик ~1 сек).
   - Семантический разбор (LLM): Google Gemini Flash (выделение сущностей, JSON-схема).
   - Поддержка офлайн-симуляции при отсутствии API-ключей.

---

## 🎨 Дизайн-система Duro

При разработке интерфейсов и Compose-компонентов строго соблюдать гайдлайны:
- **Палитра цветов:**
  - Глубокий фон: `#0C0C12` (`DuroBackground`)
  - Карточки и поверхности: `#161622` (`DuroCard`)
  - Тонкие границы: `#242436` (`DuroBorder`)
  - Акцент ритма и привычек: `#FF6B35` (`DuroOrange`)
  - Дневник и мысли: `#8B5CF6` (`DuroJournalLavender`)
  - Основной текст: `#FFFFFF`
  - Второстепенный текст: `#8A8A9E`
- **Эргономика:**
  - Минимальный размер интерактивных элементов — **48 dp** (Touch Target).
  - Отсутствие горизонтальных свайп-конфликтов между карточками и чипами.

---

## 🗺️ Карта кодовой базы (Ссылки на GitHub)

### Android-клиент (`android/app/src/main/java/com/voicehabit/tracker/`)
- **Точка входа и DI:**
  - [`MainActivity.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/MainActivity.kt)
  - [`core/di/AppContainer.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/core/di/AppContainer.kt)
- **База данных Room & DAO:**
  - База: [`data/local/db/AppDatabase.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/data/local/db/AppDatabase.kt)
  - Привычки: [`data/local/dao/HabitDao.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/data/local/dao/HabitDao.kt)
  - Задачи: [`data/local/dao/TaskDao.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/data/local/dao/TaskDao.kt)
  - Дневник: [`data/local/dao/JournalDao.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/data/local/dao/JournalDao.kt)
- **UI и экраны (Presentation):**
  - Главный экран привычек: [`presentation/home/HomeScreen.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/presentation/home/HomeScreen.kt)
  - Дневник мыслей: [`presentation/journal/JournalScreen.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/presentation/journal/JournalScreen.kt)
  - Шторка разбора записи: [`presentation/review/ReviewBottomSheet.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/presentation/review/ReviewBottomSheet.kt)
  - Таймер фокуса: [`presentation/focus/FocusScreen.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/presentation/focus/FocusScreen.kt)
  - Настройки и Obsidian: [`presentation/settings/SettingsScreen.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/presentation/settings/SettingsScreen.kt)
- **Фоновая работа:**
  - Загрузка аудио: [`data/worker/VoiceUploadWorker.kt`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/android/app/src/main/java/com/voicehabit/tracker/data/worker/VoiceUploadWorker.kt)

### Backend-микросервис (`backend/`)
- Точка входа FastAPI: [`backend/app/main.py`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/backend/app/main.py)
- Pydantic-схемы: [`backend/app/schemas.py`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/backend/app/schemas.py)
- STT (Whisper): [`backend/app/services/stt_service.py`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/backend/app/services/stt_service.py)
- LLM разбор: [`backend/app/services/llm_service.py`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/backend/app/services/llm_service.py)

---

## 📋 Бэклог задач для Notion AI Агента (Roadmap)

Выбирайте задачу, проектируйте и пишите полную реализацию:

### Направление 1: Виджеты рабочего стола Android (Glance / RemoteViews)
- Реализация интерактивного виджета на домашний экран:
  - Быстрый просмотр привычек на сегодня с возможностью чека в 1 тап.
  - Кнопка мгновенной записи голоса с запуском шторки разбора.
  - См. задачи E1, E2 в [`ROADMAP.md`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/ROADMAP.md).

### Направление 2: Аналитика и еженедельный обзор (Weekly Review & Stats)
- Экран прогресса привычек и задач:
  - График выполнения за 7 / 30 дней.
  - Определение лучших и пропущенных дней.
  - Генерация краткого AI-отчета за неделю в Obsidian / Journal.
  - См. задачу E6 в [`ROADMAP.md`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/ROADMAP.md).

### Направление 3: Умная фильтрация и теги заметок дневника
- Полнотекстовый поиск по заметкам дневника (Room FTS4).
- Автоматическое присвоение тегов мыслям (`#идея`, `#работа`, `#здоровье`, `#рефлексия`).
- См. [`IDEAS_100.md`](https://github.com/DmKOwO/voice-habit-tracker/blob/main/IDEAS_100.md).

### Направление 4: Расширение AI бэкенда
- Добавление режима еженедельного дайджеста (`/api/v1/digest/weekly`).
- Оптимизация промптов извлечения задач при сложных монологах.

---

## 🛠️ Правила сдачи кода облачным агентом

Так как агент работает в облаке Notion и не может напрямую запускать `gradlew` на ПК пользователя:
1. **Полный код файлов:** Всегда выдавать код полностью, без сокращений `// остальной код без изменений`.
2. **Точный путь к файлу:** В заголовке блока кода указывать относительный путь, например:
   `android/app/src/main/java/com/voicehabit/tracker/presentation/widget/HabitWidget.kt`
3. **Сопроводительные тесты:** При добавлении UseCase или ViewModel прикладывать модульный тест (`*Test.kt`).
4. **Инструкция по интеграции:** Указывать, в какие существующие классы (например, `AppContainer.kt` или `NavGraph.kt`) требуется добавить вызовы.
