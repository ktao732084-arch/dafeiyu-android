param([Parameter(Mandatory=$true)][string]$Sdk,[Parameter(Mandatory=$true)][string]$Jdk,[Parameter(Mandatory=$true)][string]$KeyStore)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$out=Join-Path $root 'build/upgrade-acceptance'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
$bt=Join-Path $Sdk 'build-tools/36.0.0';$jar=Join-Path $Sdk 'platforms/android-34/android.jar'
Run "$Jdk/bin/javac.exe" @('-encoding','UTF-8','--release','8','-cp',"$jar;$root/build/classes",'-d',"$out/classes","$PSScriptRoot/UpgradeAcceptance.java")
$classes=@(Get-ChildItem "$out/classes" -Filter *.class -Recurse | ForEach-Object FullName)
Run "$Jdk/bin/java.exe" (@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$jar,'--classpath',"$root/build/classes",'--min-api','26','--output',"$out/dex")+$classes)
$manifest='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.dafeiyu.releaseacceptance"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="Synthetic upgrade acceptance"/><instrumentation android:name="com.xiaoxiao.pet.UpgradeAcceptance" android:targetPackage="com.dafeiyu.companion"/></manifest>'
[IO.File]::WriteAllText("$out/AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
Run "$bt/aapt.exe" @('package','-f','-M',"$out/AndroidManifest.xml",'-I',$jar,'-F',"$out/unsigned.apk")
Push-Location "$out/dex"
try{Run "$bt/aapt.exe" @('add',"$out/unsigned.apk",'classes.dex')}finally{Pop-Location}
Run "$bt/zipalign.exe" @('-f','4',"$out/unsigned.apk","$out/aligned.apk")
$env:JAVA_HOME=$Jdk
Run "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"env:XIAOXIAO_STORE_PASS",'--out',"$out/acceptance.apk","$out/aligned.apk")
