#!/usr/bin/env bash
# Downloads Roboto (Google Fonts, Apache 2.0) into the app assets.
# If the download fails the app still works: it falls back to the system font.
set -u
DEST="$(cd "$(dirname "$0")/.." && pwd)/app/src/main/assets/fonts"
FILE="$DEST/Roboto-Variable.ttf"
URL='https://raw.githubusercontent.com/google/fonts/main/ofl/roboto/Roboto%5Bwdth%2Cwght%5D.ttf'

mkdir -p "$DEST"
if [ -s "$FILE" ]; then
  echo "Roboto already present: $FILE"
  exit 0
fi

if curl -fsSL --retry 3 --retry-delay 2 -o "$FILE" "$URL" && [ -s "$FILE" ]; then
  echo "Downloaded Roboto -> $FILE ($(wc -c < "$FILE") bytes)"
else
  rm -f "$FILE"
  echo "WARNING: could not download Roboto; the app will use the system default font." >&2
fi
exit 0
