param([string]$Sdk,[string]$Jdk,[string]$Serial)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$out=Join-Path $root 'build/core-device'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
$sources=@(Get-ChildItem "$PSScriptRoot/com/xiaoxiao/pet" -Filter *.java | ForEach-Object FullName)
Run "$Jdk/bin/javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-cp',"$Sdk/platforms/android-34/android.jar;$root/build/classes",'-d',"$out/classes")+$sources)
$classes=@(Get-ChildItem "$out/classes" -Recurse -Filter *.class | ForEach-Object FullName)
$d8=@('-cp',"$Sdk/build-tools/36.0.0/lib/d8.jar",'com.android.tools.r8.D8','--lib',"$Sdk/platforms/android-34/android.jar",'--classpath',"$root/build/classes",'--min-api','26','--output',"$out/dex")+$classes
[IO.File]::WriteAllLines("$out/d8.args",($d8 | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),[Text.UTF8Encoding]::new($false))
Run "$Jdk/bin/java.exe" @("@$out/d8.args")
$remote='/data/local/tmp/dafeiyu-core-'+[guid]::NewGuid().ToString('N')
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'shell','mkdir','-p',"$remote/memory-service/upstream/eval")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$root/assets/personas.json","$remote/personas.json")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$root/build/dex/classes.dex","$remote/product.dex")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$out/dex/classes.dex","$remote/checks.dex")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$root/memory-service/upstream/eval/cases","$remote/memory-service/upstream/eval/cases")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$root/memory-service/protocol-baseline.json","$remote/memory-service/protocol-baseline.json")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$PSScriptRoot/memory-capture","$remote/memory-capture")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'shell',"CLASSPATH=$remote/product.dex`:$remote/checks.dex",'app_process','/system/bin','com.xiaoxiao.pet.DeviceCoreChecks',$remote)
