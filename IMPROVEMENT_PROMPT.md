# Промт улучшения №1 — voice-habit-tracker

Правило цикла: работать ~100 минут, потом составить новый промт с улучшениями и продолжить.
Каждый фича должна иметь регрессионный тест или проверку на эмуляторе.
Запрещено: `git reset`, `git checkout`, `git clean`, коммиты, пуш.

## Что уже сделано (база)
- Виджеты 2×2 / 4×2 / 1×1, RemoteViews-совместимые, уникальные PendingIntent.
- Play In-App Updates + безопасная sideload-валидация манифеста.
- Задачи: секции «в работе / выполнено сегодня», типы QUICK/LONG, редактор, цвет выполнения.
- Голос: офлайн-парсер (намерение → дата → действие), `insights`, тип задачи из голоса.

## Формулировка задачи (себе)

Довести приложение до «идеала своих возможностей» за суммарно ≥8 часов работы с перерывами
на перепланирование. Неcosmметика: приоритет — целостность данных, затем воспроизводимость
голоса в фоне, затем продуктовые механики, затем доступность и полировка.

## Список фич ( Round 1 — «Данные и честность», 10 фич)

### F1. Потеря истории привычек при `INSERT OR REPLACE` [КРИТИЧНО]
`HabitEntity` + `HabitLogEntity` с `ON DELETE CASCADE`: Room открывает БД с
`PRAGMA foreign_keys = ON`, а `INSERT OR REPLACE` реализуется как `DELETE` + `INSERT`,
поэтому каждый чекбокс (`HabitRepositoryImpl.logHabitCompletion` / `toggleHabitCompletion`)
уносит всю историю привычки. Следствие: тепловая карта 28 дней всегда пустая, `completionPercentage`
всегда 0, виджеты рисуют пустые графики, `streak` показывает 13, а данных нет.
Решение: `@Upsert` (Room 2.6) + явный `UPDATE streak` вместо REPLACE.
Проверка: тест `HabitHistoryPreservedTest` на in-memory Room (прыгает в androidTest) либо
на fake-репозитории + интеграционный тест миграции.

### F2. `clearCorruptedSeedTodayLogs` удаляет ~6% логов (SQL `LIKE` wildcard)
`DELETE FROM habit_logs WHERE id LIKE 'log_%_0'`: в SQL `_` — «любой один символ»,
поэтому `log_a1b2c3d0` (реальный id из `UUID.take(8)`) совпадает с вероятностью 1/16.
Вызывается на каждом старте ViewModel. Решение: убрать подстановочную чистку, сделать
миграцию данных один раз и с явным префиксом seed-id.

### F3. Коллизии id из-за `UUID.take(8)`
32 бита энтропии → 50% коллизия на ~77k записей, тихая перезапись чужой записи вместе
с историей. Решение: полный `UUID.randomUUID().toString()` в 6 местах.

### F4. Индексы и схема Room
Нет индекса на `habit_logs.completedAt` (3 запроса фильтруют только по дате),
нет индексов на `tasks(isCompleted, dueDateIso)` и `voice_logs(status, createdAt)`.
`exportSchema = false` → нельзя проверить миграции. Решение: индексы + `exportSchema = true`
+ `room.schemaLocation` в `build.gradle.kts`.

### F5. Транзакции там, где многошаговая запись
`logHabitCompletion` (3 операции), `toggleHabitCompletion` (5 операций),
`ApplyVoiceActionsUseCase` (N вставок). Частичное применение = порча данных.
Решение: `db.withTransaction { }` в критичных местах.

### F6. Голосовой контекст берёт только открытые задачи
`VoiceRepositoryImpl` и `HomeViewModel` передают в LLM/парсер `getOpenTasksFlow()`,
поэтому фраза «сдал отчёт» не закроет задачу, выполненную сегодня. Решение: единый
источник `getAllTasksFlow()` + фильтрация «не выполнена ИЛИ выполнена сегодня».

### F7. `VoiceUploadWorker.enqueue()` не вызывается нигде
Offline-first из README не работает: без сети запись не сохраняется для повторной
обработки. Решение: реальная постановка в очередь + статусы `PENDING_UPLOAD`/`APPLIED`/`FAILED`.

### F8. Бесконечный `Result.retry()` без backoff и лимита попыток
Битый API-ключ → вечные ретраи и батарея. Решение: `runAttemptCount`, backoff,
`Result.failure` после N попыток + статус в БД.

### F9. `cancelRecording()` не удаляет файл
`stopRecording()` обнуляет `currentOutputFile` до `cancelRecording()` успевает
вызвать `delete()`. Отменённые записи остаются в `cacheDir` навсегда.
Решение: удалять файл по сохранённому пути.

### F10. Конфигурируемый виджет недостижим → все карточки 2×2 показывают одну привычку
В манифесте нет `android.appwidget.configure`, нет атрибута в `duro_habit_card_widget_info.xml`,
поэтому `DuroWidgetConfigureActivity` (199 строк) — мёртвый код, а
`configuredHabitId` всегда `null` → `allHabits.firstOrNull()`.
Решение: подключить configure-activity, `updateAppWidgetState`, `onDeleted`/`onRestored`.

## Отложено на следующие промты (кандидаты)
- Foreground-сервис записи + лимиты длительности (было: запись без `maxDuration`).
- `SpeechRecognizerHelper`: гонка `stopListening()`/`onResults`, `@Volatile liveTranscript`.
- Edge-to-edge insets (targetSdk 35) — контент уезжает под системные бары.
- Snackbar + undo для toggle/delete; подтверждение удаления; архив привычек.
- Пустые состояния, поверхность ошибок, авто-скрытие `infoMessage`, динамический год.
- Сломанные таб-фильтры (`D`/`W`/`M` смешивают frequency и displayType).
- Реальный сигнал вместо декоративной «волны» (RMS/FFT).
- `QuickRecordActivity exported=true`, `configChanges`, поворот экрана при записи.
- Обновление виджетов по `ACTION_DATE_CHANGED`/`TIMEZONE_CHANGED`/`BOOT_COMPLETED`.
- Статистика/обзор недели, расписание по дням недели, streak-freeze.
- Экспорт/импорт данных, onboarding, поиск/фильтры, AMOLED-тема.
- Уведомление воркера без `contentIntent` и без запроса `POST_NOTIFICATIONS`.
