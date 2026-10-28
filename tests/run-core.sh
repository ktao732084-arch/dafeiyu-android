#!/usr/bin/env bash
# Linux/macOS counterpart of run-core.ps1. Like it, tests run against the product classes:
# compile-check.sh builds them from src/ against Android 14 framework classes first.
# Usage: tests/run-core.sh <org.json-20240303.jar> <android-all-14-robolectric-10818077.jar>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
json="${1:?usage: tests/run-core.sh <org.json jar> <android-all jar>}"
android="${2:?usage: tests/run-core.sh <org.json jar> <android-all jar>}"
"$root/tests/compile-check.sh" "$android"
product="$root/build/compile-check/classes"
out="$root/build/core-tests"
tests="$root/tests/com/xiaoxiao/pet"
mkdir -p "$out"
javac -encoding UTF-8 -cp "$product:$json" -d "$out" "$tests/ApiEndpointTest.java" "$tests/AgentCoreTest.java" "$tests/MotionEngineTest.java" "$tests/ActionPlanTest.java" "$tests/ReplyHistoryTest.java" "$tests/NativeReplayTest.java" "$tests/HistoryRecallTest.java"
java -cp "$product:$out:$json" com.xiaoxiao.pet.AgentCoreTest "$out/state-$(date +%s)-$$"
java -cp "$product:$out" com.xiaoxiao.pet.MotionEngineTest
java -cp "$product:$out" com.xiaoxiao.pet.ApiEndpointTest
java -cp "$product:$out" com.xiaoxiao.pet.ActionPlanTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.ReplyHistoryTest
java -cp "$product:$out:$json" com.xiaoxiao.pet.NativeReplayTest "$root/memory-service"

java -cp "$product:$out:$json" com.xiaoxiao.pet.HistoryRecallTest
