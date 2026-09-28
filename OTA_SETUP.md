# OTA: запуск за 15 минут

Приложение уже настроено на канал `DmKOwO/voice-habit-tracker`: проверка при
старте + раз в сутки в фоне, автозагрузка APK, установка одним тапом через
системный инсталлер. Осталось подключить репозиторий и подпись.

## 1. Создайте репозиторий

На github.com создайте репозиторий **`DmKOwO/voice-habit-tracker`** (private — можно).

## 2. Залейте код

```bash
cd ~/Projects/voice-habit-tracker
git init
git add .
git commit -m "init"
git branch -M main
git remote add origin git@github.com:DmKOwO/voice-habit-tracker.git
git push -u origin main
```

## 3. Keystore — постоянный ключ проекта (решено!)

Все обновления приложения и OTA-релизы должны быть подписаны **строго одним сертификатом**, иначе Android выдаёт ошибку `INSTALL_FAILED_UPDATE_INCOMPATIBLE: Existing package signatures do not match newer version`.

В проект внедрён постоянный ключ:
`android/app/keystore/release.jks` (alias: `dairy`, SHA-256: `0D:04:27:F2:2C:75:27:5E:79:31:7F:CE:34:20:76:2D:99:88:1A:96:B0:FC:77:DD:F8:5D:27:48:CC:7F:DE:9C`).

1. Ключ уже настроен в GitHub Secrets репозитория (`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`).
2. В `build.gradle.kts` настроена единая подпись `appSigning` как для release, так и для debug сборок.
3. Любая локальная сборка (`assembleDebug`, `assembleRelease`) и сборка в GitHub Actions всегда используют этот ключ. Обновления теперь гарантированно встают без конфликтов подписей.

## 3.5. Ключи ИИ — чтобы работали из коробки (необязательно, но удобно)

Возьмите бесплатные ключи: [console.groq.com](https://console.groq.com) (Groq,
распознавание речи) и [Google AI Studio](https://aistudio.google.com) (Gemini,
разбор команд). Добавьте в те же Secrets:

| Secret         | Значение       |
|----------------|----------------|
| `GROQ_API_KEY`   | `gsk_...`      |
| `GEMINI_API_KEY` | `AIza...`      |

CI встроит их в APK — приложение сразу распознаёт речь, ничего вставлять
в настройках не нужно. Свои ключи в настройках перекрывают встроенные.
Без секретов работают офлайн-парсер и ручной ввод.

## 4. Первый релиз

```bash
./release.sh 1.0.1
```

CI соберёт APK (`versionName` из тега, `versionCode` из номера запуска) и
опубликует его в **Releases**. Проверка: `gh release view v1.0.1`.

Версия обязана быть **выше** установленной на телефоне, иначе OTA её не увидит.

## 5. Телефон: первая установка — вручную

Скачайте APK из Releases и установите. Дальше всё само:

1. Приложение проверяет релизы при старте и раз в сутки в фоне.
2. Новый релиз **скачивается автоматически**, приходит уведомление.
3. Тап «Установить» в Настройки → GitHub → системный инсталлер → Готово.

## Правила, которые нельзя нарушать

- Не теряйте `upload-keystore.jks` и не коммитьте его в git (уже в `.gitignore`).
- Не понижайте версию: каждый релиз — новый тег выше предыдущего.
- Один тег = один релиз, теги не перезаписывать.
