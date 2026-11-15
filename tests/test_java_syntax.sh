#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
BUILD_DIR="$(mktemp -d)"
trap 'rm -rf "$BUILD_DIR"' EXIT
javac -encoding UTF-8 -d "$BUILD_DIR" tests/JavaSyntaxTest.java
java -cp "$BUILD_DIR" JavaSyntaxTest
