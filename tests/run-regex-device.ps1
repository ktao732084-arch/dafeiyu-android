param([string]$Sdk,[string]$Jdk,[string]$Serial)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$out=Join-Path $root 'build/regex-device'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
Run "$Jdk/bin/javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-cp',"$Sdk/platforms/android-34/android.jar;$root/build/classes",'-d',"$out/classes","$PSScriptRoot/com/xiaoxiao/pet/NativeRegexTest.java")
Run "$Jdk/bin/java.exe" @('-cp',"$Sdk/build-tools/36.0.0/lib/d8.jar",'com.android.tools.r8.D8','--lib',"$Sdk/platforms/android-34/android.jar",'--classpath',"$root/build/classes",'--min-api','26','--output',"$out/dex","$out/classes/com/xiaoxiao/pet/NativeRegexTest.class")
$remote='/data/local/tmp/dafeiyu-regex-'+[guid]::NewGuid().ToString('N')
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'shell','mkdir','-p',$remote)
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$root/build/dex/classes.dex","$remote/product.dex")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'push',"$out/dex/classes.dex","$remote/checks.dex")
Run "$Sdk/platform-tools/adb.exe" @('-s',$Serial,'shell',"CLASSPATH=$remote/product.dex`:$remote/checks.dex",'app_process','/system/bin','com.xiaoxiao.pet.NativeRegexTest')
