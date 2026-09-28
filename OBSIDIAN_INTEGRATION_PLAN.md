# План интеграции с Obsidian (Obsidian Vault Integration MVP)

## 1. Введение и концепция
Приложение **Voice Habit & Task Tracker** (Duro Edition) переходит от роли простого трекера привычек к персональной операционной системе мышления и продуктивности. Центральным элементом новой архитектуры является **Дневник мыслей (Voice Journal)**.

Для пользователей философии **Local-First** и методологии управления знаниями (PKM / Zettelkasten / Second Brain) **Obsidian** является стандартом де-факто благодаря:
- Полному отсутствию vendor lock-in (чистый Markdown на локальной файловой системе).
- Мощному языку запросов **Dataview**.
- Двунаправленным ссылкам (`[[wikilinks]]`) и графу связей.
- Синхронизации через Git, Syncthing, iCloud или Obsidian Sync.

Данный документ представляет детальный, практически реализуемый план MVP-интеграции Android-клиента с локальным хранилищем Obsidian (Vault).

---

## 2. Архитектура доступа к хранилищу (Storage Access Framework)

### 2.1 Выбор директории хранилища (SAF Folder Picker)
Android 11+ (API 30+) жестко ограничивает прямой доступ к файловой системе через Scoped Storage. Для надежной записи файлов в произвольный Obsidian Vault используется **Storage Access Framework (SAF)**:

```kotlin
// Запрос на выбор папки хранилища пользователем
val openVaultFolderLauncher = registerForActivityResult(
    ActivityResultContracts.OpenDocumentTree()
) { uri: Uri? ->
    uri?.let { treeUri ->
        // Захват постоянных прав доступа (persist across reboots)
        val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        contentResolver.takePersistableUriPermission(treeUri, takeFlags)
        
        // Сохранение Uri в SharedPreferences / SettingsManager
        settingsManager.saveObsidianVaultUri(treeUri.toString())
    }
}
```

### 2.2 Структура файлов в Obsidian Vault
Внутри выбранного Vault приложение автоматически организует чистую и изолированную структуру:

```text
MyObsidianVault/
├── Voice Journal/
│   ├── attachments/
│   │   ├── voice_2026-09-28_143000.m4a
│   │   └── voice_2026-09-28_214500.m4a
│   ├── 2026-09-28 Фокус и глубинная продуктивность.md
│   └── 2026-09-28 Вечерняя рефлексия баланс сил.md
```

---

## 3. Спецификация формата заметки Markdown

Каждая мысль экспортируется в виде валидного файла `.md` с богатым YAML Frontmatter и семантическими блоками.

### 3.1 Пример экспортированного файла
```markdown
---
id: "journal_a1b2c3d4"
title: "Фокус и глубинная продуктивность недели"
date: 2026-09-28 14:30:00
updated: 2026-09-28 14:30:00
type: voice-journal
mood: "⚡ Инсайт"
tone: "Осознанный"
audio: "attachments/voice_2026-09-28_143000.m4a"
duration_seconds: 42
source: "Voice Habit Tracker (Duro)"
tags:
  - voice-journal
  - focus
  - insight
  - productivity
---

# Фокус и глубинная продуктивность недели

> [!quote] Ключевой инсайт
> Настоящий прогресс рождается не из количества закрытых тикетов, а из чистоты ума и непрерывных блоков глубокой работы без микроменеджмента.

## 📌 Ключевые тезисы
- Утренний блок в 90 минут без уведомлений дает 80% дневного результата.
- Перегруженность задачами рассеивает внимание и снижает качество архитектурных решений.
- Регулярная фиксация мыслей голосом освобождает оперативную память мозга.

## 🎯 Принятые решения
- [x] Перенести все мелкие созвоны строго на вторую половину дня
- [x] Вести голосовой дневник мыслей каждый вечер

## 🚀 Следующие шаги (Action Items)
- [ ] Настроить режим «Не беспокоить» на телефоне с 9:00 до 12:00
- [ ] Провести ревизию открытых задач и удалить неактуальные

## 🎙 Стенограмма аудиозаписи
Сегодня утром я окончательно осознал, что если сразу открывать рабочий мессенджер, весь день разбивается на чужие приоритеты. 90 минут непрерывного погружения с утра закрывают фундаментальные задачи...
```

### 3.2 Преимущества схемы:
1. **Поддержка Obsidian Callouts (`> [!quote]`):** В мобильном и десктопном Obsidian инсайт выделяется акцентной плашкой с иконкой цитаты.
2. **Интерактивные чекбоксы (`- [ ]` / `- [x]`):** Плагины Obsidian Tasks и Dataview автоматически подхватывают задачи из секции `## 🚀 Следующие шаги`.
3. **Локальное аудио (`attachments/`):** Пользователь может нажать на встроенный аудиоплеер прямо внутри заметки Obsidian и прослушать оригинальный голос.

---

## 4. Глубокая интеграция и ссылки (Deep Linking)

### 4.1 Открытие заметки в Obsidian из Android-приложения
При нажатии на карточку записи в Дневнике или в детальном окне пользователь может открыть эту заметку напрямую в приложении Obsidian с помощью официальной схемы `obsidian://`:

```kotlin
fun openNoteInObsidian(context: Context, vaultName: String, relativeFilePath: String) {
    val encodedVault = Uri.encode(vaultName)
    val encodedFile = Uri.encode(relativeFilePath)
    val obsidianUri = Uri.parse("obsidian://open?vault=$encodedVault&file=$encodedFile")

    val intent = Intent(Intent.ACTION_VIEW, obsidianUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "Приложение Obsidian не установлено", Toast.LENGTH_SHORT).show()
    }
}
```

---

## 5. Движок синхронизации (WorkManager)

### 5.1 Автоматический экспорт при сохранении
Когда пользователь сохраняет новую запись мысли в `ReviewBottomSheet` или закрывает аудиозапись:
1. Запись сохраняется в локальную Room БД (`JournalEntryEntity`).
2. Если в настройках подключен Obsidian Vault:
   - Запускается легкий `ObsidianSyncWorker` (через Android WorkManager).
   - Файл аудио (`.m4a`) копируется в `Voice Journal/attachments/`.
   - Файл `.md` генерируется и атомарно записывается через SAF `DocumentFile.createFile()`.
3. Пользователь видит мгновенную всплывающую подсказку или тихий индикатор статуса синхронизации.

---

## 6. Пользовательский интерфейс (UI Flow)

1. **В деталях мысли (`JournalDetailSheet`):**
   - Кнопка **«Obsidian MD»**: немедленно формирует валидный Markdown с Frontmatter и копирует в буфер обмена Android (работает прямо сейчас в текущей сборке!).
2. **В меню профиля (`ProfileMenuSheet`):**
   - Пункт **«Синхронизация с Obsidian Vault»**:
     - Кнопка «Выбрать папку Vault на устройстве».
     - Статус подключения (зеленый индикатор: *Подключено к /storage/emulated/0/Documents/Obsidian/Vault*).
     - Переключатель: «Авто-экспорт новых записей».
     - Кнопка: «Экспортировать все прошлые записи в хранилище».

---

## 7. Этапы внедрения (Roadmap)

| Этап | Задача | Статус |
|------|--------|--------|
| **Этап 1** | Модель данных `JournalEntry`, генерация Markdown с YAML Frontmatter, кнопка копирования в буфер | ✅ **Выполнено в v2.0** |
| **Этап 2** | SAF Folder Picker и сохранение Persistable Uri в `SettingsManager` | Готово к включению |
| **Этап 3** | Фоновый воркер `ObsidianExportWorker` для копирования `.m4a` и `.md` | Запланировано в v2.1 |
| **Этап 4** | Двусторонняя синхронизация: чтение изменений из Obsidian назад в Room БД | Исследование v2.2 |
