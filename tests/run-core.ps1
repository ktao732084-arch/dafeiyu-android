param([string]$Jdk,[string]$JsonJar)
$ErrorActionPreference='Stop'
$Root=Split-Path $PSScriptRoot
$Output=Join-Path $Root 'build\core-tests'
New-Item -ItemType Directory -Force $Output | Out-Null
$Source=Join-Path $Root 'src\com\xiaoxiao\pet'
& "$Jdk/bin/javac.exe" -encoding UTF-8 -cp $JsonJar -d $Output "$Source/ApiEndpoint.java" "$Source/AgentPolicy.java" "$Source/AgentStore.java" "$Source/AgentReply.java" "$Source/MotionEngine.java" "$PSScriptRoot/com/xiaoxiao/pet/ApiEndpointTest.java" "$PSScriptRoot/com/xiaoxiao/pet/AgentCoreTest.java" "$PSScriptRoot/com/xiaoxiao/pet/MotionEngineTest.java"
if($LASTEXITCODE -ne 0){throw 'Core compilation failed'}
$State=Join-Path $Output ('state-'+[guid]::NewGuid().ToString('N'))
& "$Jdk/bin/java.exe" -cp "$Output;$JsonJar" com.xiaoxiao.pet.AgentCoreTest $State
if($LASTEXITCODE -ne 0){throw 'Agent core tests failed'}
& "$Jdk/bin/java.exe" -cp $Output com.xiaoxiao.pet.MotionEngineTest
if($LASTEXITCODE -ne 0){throw 'Motion tests failed'}

& "$Jdk/bin/java.exe" -cp $Output com.xiaoxiao.pet.ApiEndpointTest
if($LASTEXITCODE -ne 0){throw 'API endpoint tests failed'}
