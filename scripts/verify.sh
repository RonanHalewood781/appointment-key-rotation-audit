#!/usr/bin/env sh
set -eu

BUILD_DIR="${TMPDIR:-/tmp}/appointment-key-audit-classes"
rm -rf "$BUILD_DIR"
mkdir -p "$BUILD_DIR"
find src/main/java src/test/java -name '*.java' -print > "$BUILD_DIR/sources.txt"
javac -d "$BUILD_DIR" @"$BUILD_DIR/sources.txt"
java -cp "$BUILD_DIR" org.example.healthtech.AppointmentNoticePolicyTest
