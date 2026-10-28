#!/usr/bin/env bash
# Linux/macOS counterpart of run-core.ps1: JVM core, motion and endpoint checks.
# Usage: tests/run-core.sh <org.json-20240303.jar>
set -euo pipefail
root="$(cd "$(dirname "$0")/.." && pwd)"
json="${1:?usage: tests/run-core.sh <org.json jar>}"
out="$root/build/core-tests"
src="$root/src/com/xiaoxiao/pet"
tests="$root/tests/com/xiaoxiao/pet"
mkdir -p "$out"
javac -encoding UTF-8 -cp "$json" -d "$out" "$src/ApiEndpoint.java" "$src/AgentPolicy.java" "$src/AgentStore.java" "$src/AgentReply.java" "$src/MotionEngine.java" "$tests/ApiEndpointTest.java" "$tests/AgentCoreTest.java" "$tests/MotionEngineTest.java"
java -cp "$out:$json" com.xiaoxiao.pet.AgentCoreTest "$out/state-$(date +%s)-$$"
java -cp "$out" com.xiaoxiao.pet.MotionEngineTest
java -cp "$out" com.xiaoxiao.pet.ApiEndpointTest
