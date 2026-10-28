#!/usr/bin/env bash
# Compiles every app source against the Android 14 framework classes, without the Android SDK.
# This catches Java/API errors only; it does not build resources, dex or an APK.
# Usage: tests/compile-check.sh <android-all-14-robolectric-10818077.jar>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
android="${1:?usage: tests/compile-check.sh <android-all jar>}"
out="$root/build/compile-check"
rm -rf "$out" && mkdir -p "$out/stub/com/dafeiyu/companion" "$out/classes"
# aapt2 normally generates R; a stub with the referenced style ids is enough for javac.
styles=$(grep -oh 'com\.dafeiyu\.companion\.R\.style\.[A-Za-z_]*' "$root"/src/com/xiaoxiao/pet/*.java | sed 's/.*\.//' | sort -u | awk '{printf "%s%s=%d", (NR>1?",":""), $0, NR}')
printf 'package com.dafeiyu.companion;\npublic final class R { public static final class style { public static final int %s; } }\n' "$styles" > "$out/stub/com/dafeiyu/companion/R.java"
javac -encoding UTF-8 --release 8 -nowarn -cp "$android" -d "$out/classes" "$out/stub/com/dafeiyu/companion/R.java" "$root"/src/com/xiaoxiao/pet/*.java
echo "PASS: app sources compile against Android 14 at Java 8 level"
