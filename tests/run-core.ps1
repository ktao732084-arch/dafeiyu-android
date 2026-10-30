param([string]$Jdk,[string]$JsonJar)
$ErrorActionPreference='Stop'
$Root=Split-Path $PSScriptRoot
$Output=Join-Path $Root 'build\core-tests'
New-Item -ItemType Directory -Force $Output | Out-Null
$Source=Join-Path $Root 'src\com\xiaoxiao\pet'
$Product=Join-Path $Root 'build/classes'
if(!(Test-Path -LiteralPath "$Product/com/xiaoxiao/pet/AgentStore.class")){throw 'Build the APK first so tests use the exact product classes.'}
& "$Jdk/bin/javac.exe" -encoding UTF-8 -cp "$Product;$JsonJar" -d $Output "$PSScriptRoot/com/xiaoxiao/pet/ApiEndpointTest.java" "$PSScriptRoot/com/xiaoxiao/pet/AgentCoreTest.java" "$PSScriptRoot/com/xiaoxiao/pet/MotionEngineTest.java" "$PSScriptRoot/com/xiaoxiao/pet/SuspensionMotionTest.java" "$PSScriptRoot/com/xiaoxiao/pet/ActionPlanTest.java" "$PSScriptRoot/com/xiaoxiao/pet/ReplyHistoryTest.java" "$PSScriptRoot/com/xiaoxiao/pet/NativeReplayTest.java" "$PSScriptRoot/com/xiaoxiao/pet/EpisodeRecallTest.java" "$PSScriptRoot/com/xiaoxiao/pet/MemoryCaptureTest.java" "$PSScriptRoot/com/xiaoxiao/pet/CorrectionIntegrityTest.java" "$PSScriptRoot/com/xiaoxiao/pet/RewriteGuardTest.java" "$PSScriptRoot/com/xiaoxiao/pet/DiaryTest.java" "$PSScriptRoot/com/xiaoxiao/pet/CommitmentsTest.java" "$PSScriptRoot/com/xiaoxiao/pet/HistoryRecallTest.java" "$PSScriptRoot/com/xiaoxiao/pet/NativeRegexTest.java" "$PSScriptRoot/com/xiaoxiao/pet/ChatPolishRulesTest.java" "$PSScriptRoot/com/xiaoxiao/pet/InitiativeTest.java" "$PSScriptRoot/com/xiaoxiao/pet/UsabilityTest.java" "$PSScriptRoot/com/xiaoxiao/pet/UpdateCoreTest.java" "$PSScriptRoot/com/xiaoxiao/pet/CharacterTest.java" "$PSScriptRoot/com/xiaoxiao/pet/SituationsTest.java" "$PSScriptRoot/com/xiaoxiao/pet/AliveTest.java" "$PSScriptRoot/com/xiaoxiao/pet/CharacterStateTest.java" "$PSScriptRoot/com/xiaoxiao/pet/PersonaEvalDump.java" "$PSScriptRoot/com/xiaoxiao/pet/ScenarioEval.java" "$PSScriptRoot/com/xiaoxiao/pet/GrowthTest.java" "$PSScriptRoot/com/xiaoxiao/pet/TouchReplyTest.java" "$PSScriptRoot/com/xiaoxiao/pet/GomokuTest.java"
if($LASTEXITCODE -ne 0){throw 'Core compilation failed'}
$State=Join-Path $Output ('state-'+[guid]::NewGuid().ToString('N'))
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.AgentCoreTest $State
if($LASTEXITCODE -ne 0){throw 'Agent core tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output" com.xiaoxiao.pet.MotionEngineTest
if($LASTEXITCODE -ne 0){throw 'Motion tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output" com.xiaoxiao.pet.SuspensionMotionTest
if($LASTEXITCODE -ne 0){throw 'Suspension tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output" com.xiaoxiao.pet.ApiEndpointTest
if($LASTEXITCODE -ne 0){throw 'API endpoint tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output" com.xiaoxiao.pet.ActionPlanTest
if($LASTEXITCODE -ne 0){throw 'Action plan tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.ReplyHistoryTest
if($LASTEXITCODE -ne 0){throw 'Reply/history tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.NativeReplayTest (Join-Path $Root 'memory-service')
if($LASTEXITCODE -ne 0){throw 'Native memory replay failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.HistoryRecallTest
if($LASTEXITCODE -ne 0){throw 'History recall tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.EpisodeRecallTest (Join-Path $Output ('episodes-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Episodic recall tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.MemoryCaptureTest (Join-Path $PSScriptRoot 'memory-capture') (Join-Path $Root 'memory-service')
if($LASTEXITCODE -ne 0){throw 'Memory capture tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.DiaryTest (Join-Path $Output ('diary-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Diary tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.CommitmentsTest (Join-Path $Output ('commitments-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Commitment tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.NativeRegexTest
if($LASTEXITCODE -ne 0){throw 'Native regex tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.ChatPolishRulesTest
if($LASTEXITCODE -ne 0){throw 'Chat polish rules failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.InitiativeTest
if($LASTEXITCODE -ne 0){throw 'Initiative tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.UsabilityTest (Join-Path $Output ('usability-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Usability tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.GomokuTest
if($LASTEXITCODE -ne 0){throw 'Gomoku tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.UpdateCoreTest (Join-Path $Output ('updates-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Update core checks failed'}


& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.CharacterTest (Join-Path $PSScriptRoot "../assets/personas.json") (Join-Path $Output ('character-'+[guid]::NewGuid().ToString('N')))
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.CharacterStateTest (Join-Path $Output ('character-state-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Character tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.SituationsTest (Join-Path $PSScriptRoot "../assets/personas.json")
if($LASTEXITCODE -ne 0){throw 'Situation module tests failed'}
& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.AliveTest (Join-Path $Output ('alive-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Alive tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.GrowthTest $Root (Join-Path $Output ('growth-'+[guid]::NewGuid().ToString('N')))
if($LASTEXITCODE -ne 0){throw 'Growth tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.TouchReplyTest
if($LASTEXITCODE -ne 0){throw 'Touch-reply tests failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.RewriteGuardTest
if($LASTEXITCODE -ne 0){throw 'Rewrite fidelity checks failed'}

& "$Jdk/bin/java.exe" -cp "$Product;$Output;$JsonJar" com.xiaoxiao.pet.CorrectionIntegrityTest
if($LASTEXITCODE -ne 0){throw 'Correction/integrity tests failed'}
