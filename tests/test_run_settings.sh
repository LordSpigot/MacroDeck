#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
javac -encoding UTF-8 -d "$BUILD_DIR" \
    tests/android/content/SharedPreferences.java \
    tests/android/content/Context.java \
    app/src/main/java/at/macrodeck/app/data/RunSettingsStore.java \
    tests/RunSettingsStoreTest.java
java -cp "$BUILD_DIR" RunSettingsStoreTest
