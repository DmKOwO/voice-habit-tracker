#!/usr/bin/env bash
# Публикация релиза: поднимает версию в version.properties, коммитит,
# ставит тег и пушит в GitHub, запуская сборку CI и выкладку APK в Releases (DmKOwO).
# Использование:
#   ./release.sh           (автоматически поднимет patch: например, 1.0.1 -> 1.0.2)
#   ./release.sh 1.0.2     (установит указанную версию SemVer)
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
VERSION_FILE="$SCRIPT_DIR/android/version.properties"

# Текущая версия из файла (если файла нет — по умолчанию 1.0.0, code 1)
CURRENT_VER="1.0.0"
CURRENT_CODE=1
if [ -f "$VERSION_FILE" ]; then
  CURRENT_VER=$(grep '^VERSION_NAME=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' \r\n' || echo "1.0.0")
  CURRENT_CODE=$(grep '^VERSION_CODE=' "$VERSION_FILE" | cut -d'=' -f2 | tr -d ' \r\n' || echo "1")
  if [ -z "$CURRENT_VER" ]; then CURRENT_VER="1.0.0"; fi
  if [ -z "$CURRENT_CODE" ]; then CURRENT_CODE=1; fi
fi

VER="${1:-}"
if [ -z "$VER" ]; then
  # Автоматический инкремент patch версии: X.Y.Z -> X.Y.(Z+1)
  IFS='.' read -r major minor patch <<< "$CURRENT_VER"
  patch=$((patch + 1))
  VER="$major.$minor.$patch"
  echo "Версия не указана. Автоматический инкремент: $CURRENT_VER -> $VER"
fi

if ! [[ "$VER" =~ ^[0-9]+\.[0-9]+\.[0-9]+(-[0-9A-Za-z.-]+)?$ ]]; then
  echo "Ошибка: версия '$VER' должна быть в формате SemVer (например 1.0.1 или 1.2.0)."
  exit 1
fi

NEW_CODE=$((CURRENT_CODE + 1))

echo "Запись версии в $VERSION_FILE:"
echo "  VERSION_NAME=$VER"
echo "  VERSION_CODE=$NEW_CODE"

cat <<EOF > "$VERSION_FILE"
VERSION_NAME=$VER
VERSION_CODE=$NEW_CODE
EOF

TAG="v$VER"

if ! git rev-parse --is-inside-work-tree >/dev/null 2>&1; then
  echo "Предупреждение: Git не инициализирован в проекте."
  echo "Файл $VERSION_FILE обновлен (v$VER). Для публикации через GitHub Actions выполните шаги из OTA_SETUP.md."
  exit 0
fi

if git rev-parse "$TAG" >/dev/null 2>&1; then
  echo "Ошибка: тег $TAG уже существует в git."
  exit 1
fi

git add "$VERSION_FILE"
git commit -m "Bump version to $TAG" || true
git tag "$TAG"

if git remote | grep -q 'origin'; then
  git push origin HEAD --tags
  echo "✅ Тег $TAG и код запушены. CI соберёт APK и опубликует Release."
  echo "Статус: gh run watch  |  Просмотр: gh release view $TAG"
else
  echo "✅ Тег $TAG создан локально. Подключите origin и выполните git push --tags."
fi
