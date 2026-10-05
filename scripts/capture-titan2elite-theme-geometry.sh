#!/usr/bin/env bash

set -euo pipefail

SERIAL="${1:-emulator-5560}"
OUTPUT_DIR="${2:-build/titan2elite-theme-geometry}"
ADB_BIN="${ADB_BIN:-${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb}"
MAESTRO_BIN="${MAESTRO_BIN:-$HOME/.maestro/bin/maestro}"
FFMPEG_BIN="${FFMPEG_BIN:-ffmpeg}"
PACKAGE="${PASTIERA_PACKAGE:-app.numen.keyboard}"
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

THEMES=(
  "Pastiera Dark"
  "Pastiera Light"
  "Cloud Tap"
  "Moon Tap"
  "Classic Cloud"
  "Classic Midnight"
  "ePaper"
  "High Contrast"
  "Warm"
  "Solarized Dark"
  "Solarized Light"
  "Monokai"
  "Dracula"
  "Nord"
  "Volcanic Dusk"
)

for command in "$ADB_BIN" "$MAESTRO_BIN" "$FFMPEG_BIN"; do
  if [[ ! -x "$command" ]] && ! command -v "$command" >/dev/null 2>&1; then
    echo "Missing required command: $command" >&2
    exit 1
  fi
done

mkdir -p "$OUTPUT_DIR/raw"

"$ADB_BIN" -s "$SERIAL" get-state >/dev/null
"$ADB_BIN" -s "$SERIAL" shell wm size 1080x1320
"$ADB_BIN" -s "$SERIAL" shell wm density 400
"$ADB_BIN" -s "$SERIAL" shell settings put system font_scale 1.0

"$ADB_BIN" -s "$SERIAL" shell am start -W -f 0x10008000 \
  -n "$PACKAGE/it.palsoftware.pastiera.MainActivity"
"$MAESTRO_BIN" --device "$SERIAL" test \
  "$ROOT_DIR/maestro/titan2elite/focus-ime-test-field.yaml"
"$ADB_BIN" -s "$SERIAL" shell am start -W \
  -a android.intent.action.MAIN \
  -n "$PACKAGE/it.palsoftware.pastiera.SettingsActivity" \
  --es it.palsoftware.pastiera.SETTINGS_DESTINATION customization \
  --es it.palsoftware.pastiera.CUSTOMIZATION_DESTINATION keyboard_theme
"$MAESTRO_BIN" --device "$SERIAL" test \
  "$ROOT_DIR/maestro/titan2elite/reset-hardware-theme-list.yaml"

index=0
for theme in "${THEMES[@]}"; do
  index=$((index + 1))
  slug="$(printf '%s' "$theme" | tr '[:upper:]' '[:lower:]' | tr ' ' '-')"
  prefix="$(printf '%02d' "$index")-$slug"

  if [[ "$index" -gt 1 ]]; then
    "$ADB_BIN" -s "$SERIAL" shell am start -W \
      -a android.intent.action.MAIN \
      -n "$PACKAGE/it.palsoftware.pastiera.SettingsActivity" \
      --es it.palsoftware.pastiera.SETTINGS_DESTINATION customization \
      --es it.palsoftware.pastiera.CUSTOMIZATION_DESTINATION keyboard_theme
  fi
  "$MAESTRO_BIN" --device "$SERIAL" test -e "THEME_NAME=$theme" \
    "$ROOT_DIR/maestro/titan2elite/select-hardware-theme.yaml"

  "$ADB_BIN" -s "$SERIAL" shell am start -W -f 0x10008000 \
    -n "$PACKAGE/it.palsoftware.pastiera.MainActivity"
  "$MAESTRO_BIN" --device "$SERIAL" test \
    "$ROOT_DIR/maestro/titan2elite/focus-ime-test-field.yaml"

  "$ADB_BIN" -s "$SERIAL" exec-out screencap -p > "$OUTPUT_DIR/raw/$prefix.png"
  "$FFMPEG_BIN" -loglevel error -y -i "$OUTPUT_DIR/raw/$prefix.png" \
    -vf "crop=1080:1200:0:0" "$OUTPUT_DIR/$prefix.png"
done

echo "Captured ${#THEMES[@]} cropped 1080x1200 theme geometry screenshots in $OUTPUT_DIR"
