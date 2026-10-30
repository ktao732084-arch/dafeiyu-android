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
javac -encoding UTF-8 -cp "$product:$json" -d "$out" "$tests/ApiEndpointTest.java" "$tests/AgentCoreTest.java" "$tests/MotionEngineTest.java" "$tests/SuspensionMotionTest.java" "$tests/ActionPlanTest.java" "$tests/ReplyHistoryTest.java" "$tests/NativeReplayTest.java" "$tests/EpisodeRecallTest.java" "$tests/MemoryCaptureTest.java" "$tests/CorrectionIntegrityTest.java" "$tests/RewriteGuardTest.java" "$tests/DiaryTest.java" "$tests/CommitmentsTest.java" "$tests/HistoryRecallTest.java" "$tests/NativeRegexTest.java" "$tests/ChatPolishRulesTest.java" "$tests/InitiativeTest.java" "$tests/UsabilityTest.java" "$tests/UpdateCoreTest.java" "$tests/CharacterTest.java" "$tests/SituationsTest.java" "$tests/AliveTest.java" "$tests/CharacterStateTest.java" "$tests/PersonaEvalDump.java" "$tests/ScenarioEval.java" "$tests/GrowthTest.java" "$tests/TouchReplyTest.java" "$tests/GomokuTest.java"
java -cp "$product:$out:$json" com.xiaoxiao.pet.AgentCoreTest "$out/state-$(date +%s)-$$"
java -cp "$product:$out" com.xiaoxiao.pet.MotionEngineTest
java -cp "$product:$out" com.xiaoxiao.pet.SuspensionMotionTest
java -cp "$product:$out" com.xiaoxiao.pet.ApiEndpointTest
java -cp "$product:$out" com.xiaoxiao.pet.ActionPlanTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.ReplyHistoryTest
java -cp "$product:$out:$json" com.xiaoxiao.pet.NativeReplayTest "$root/memory-service"

java -cp "$product:$out:$json" com.xiaoxiao.pet.HistoryRecallTest
java -cp "$product:$out:$json" com.xiaoxiao.pet.EpisodeRecallTest "$out/episodes-$(date +%s)-$$"
java -cp "$product:$out:$json" com.xiaoxiao.pet.MemoryCaptureTest "$root/tests/memory-capture" "$root/memory-service"
java -cp "$product:$out:$json" com.xiaoxiao.pet.DiaryTest "$out/diary-$(date +%s)-$$"
java -cp "$product:$out:$json" com.xiaoxiao.pet.CommitmentsTest "$out/commitments-$(date +%s)-$$"

java -cp "$product:$out:$json" com.xiaoxiao.pet.NativeRegexTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.ChatPolishRulesTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.InitiativeTest
java -cp "$product:$out:$json" com.xiaoxiao.pet.UsabilityTest "$out/usability-$(date +%s)-$$"

java -cp "$product:$out:$json" com.xiaoxiao.pet.GomokuTest
java -cp "$product:$out:$json" com.xiaoxiao.pet.UpdateCoreTest "$out/updates-$(date +%s)-$$"


java -cp "$product:$out:$json" com.xiaoxiao.pet.CharacterTest "$root/assets/personas.json" "$out/character-$(date +%s)-$$"
java -cp "$product:$out:$json" com.xiaoxiao.pet.SituationsTest "$root/assets/personas.json"
java -cp "$product:$out:$json" com.xiaoxiao.pet.AliveTest "$out/alive-$(date +%s)-$$"
java -cp "$product:$out:$json" com.xiaoxiao.pet.CharacterStateTest "$out/character-state-$(date +%s)-$$"

java -cp "$product:$out:$json" com.xiaoxiao.pet.GrowthTest "$root" "$out/growth-$(date +%s)-$$"

java -cp "$product:$out:$json" com.xiaoxiao.pet.TouchReplyTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.RewriteGuardTest

java -cp "$product:$out:$json" com.xiaoxiao.pet.CorrectionIntegrityTest
