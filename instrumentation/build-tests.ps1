param([string]$Sdk,[string]$Jdk,[string]$KeyStore)
$ErrorActionPreference='Stop'
$Root=$PSScriptRoot
$Bt=Join-Path $Sdk 'build-tools\36.0.0'
$Jar=Join-Path $Sdk 'platforms\android-34\android.jar'
$Build=Join-Path $Root 'build'
New-Item -ItemType Directory -Force "$Build/classes","$Build/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
Run "$Jdk/bin/javac.exe" @('-encoding','UTF-8','-source','8','-target','8','-cp',"$Jar;$(Split-Path $Root)/build/classes",'-d',"$Build/classes","$Root/AgentInstrumentation.java")
$Classes=@(Get-ChildItem "$Build/classes" -Recurse -Filter *.class | ForEach-Object FullName)
$D8Args=@('-cp',"$Bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$Jar,'--classpath',"$(Split-Path $Root)/build/classes",'--min-api','26','--output',"$Build/dex")+$Classes
[IO.File]::WriteAllLines("$Build/d8.args",($D8Args | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),[Text.UTF8Encoding]::new($false))
Run "$Jdk/bin/java.exe" @("@$Build/d8.args")
Run "$Bt/aapt.exe" @('package','-f','-M',"$Root/AndroidManifest.xml",'-I',$Jar,'-F',"$Build/unsigned.apk")
Push-Location "$Build/dex"
try{Run "$Bt/aapt.exe" @('add',"$Build/unsigned.apk",'classes.dex')}finally{Pop-Location}
Run "$Bt/zipalign.exe" @('-f','4',"$Build/unsigned.apk","$Build/aligned.apk")
$env:JAVA_HOME=$Jdk
Run "$Bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"pass:$env:XIAOXIAO_STORE_PASS",'--out',"$Build/runtime-tests.apk","$Build/aligned.apk")
