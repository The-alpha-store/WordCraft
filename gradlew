#!/usr/bin/env sh
# Small Gradle bootstrapper. Android Studio can also import this project directly.
set -eu
GRADLE_VERSION="8.9"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$HOME/.gradle}"
INSTALL_DIR="$GRADLE_USER_HOME/wordcraft-gradle-$GRADLE_VERSION"
GRADLE_BIN="$INSTALL_DIR/gradle-$GRADLE_VERSION/bin/gradle"
if [ ! -x "$GRADLE_BIN" ]; then
  mkdir -p "$INSTALL_DIR"
  ARCHIVE="$INSTALL_DIR/gradle-$GRADLE_VERSION-bin.zip"
  URL="https://services.gradle.org/distributions/gradle-$GRADLE_VERSION-bin.zip"
  if command -v curl >/dev/null 2>&1; then
    curl -fL "$URL" -o "$ARCHIVE"
  elif command -v wget >/dev/null 2>&1; then
    wget -O "$ARCHIVE" "$URL"
  else
    echo "Install curl or wget to download Gradle $GRADLE_VERSION." >&2
    exit 1
  fi
  unzip -q -o "$ARCHIVE" -d "$INSTALL_DIR"
  rm -f "$ARCHIVE"
fi
exec "$GRADLE_BIN" "$@"
