#!/usr/bin/env bash
# Replays the 84 shared memory scenarios through the Python service (UTC and UTC+8) and through the
# phone's LocalEngine; every write call must leave identical memories, archive, audit and rejections.
# Run tests/run-core.sh first (it builds the product classes). Usage: tests/local-engine/run.sh <org.json jar>
set -euo pipefail
root="$(cd "$(dirname "$0")/../.." && pwd)"
json="${1:?usage: tests/local-engine/run.sh <org.json jar>}"
out="$root/build/local-engine"
mkdir -p "$out"
python3 -X utf8 "$root/tests/local-engine/replay.py" "$out/replay-utc.json" 0
python3 -X utf8 "$root/tests/local-engine/replay.py" "$out/replay-utc8.json" 480
javac -encoding UTF-8 -cp "$root/build/compile-check/classes:$json" -d "$out/classes" "$root/tests/com/xiaoxiao/pet/LocalEngineReplay.java"
java -cp "$root/build/compile-check/classes:$out/classes:$json" com.xiaoxiao.pet.LocalEngineReplay "$out/replay-utc.json" "$out/replay-utc8.json"
