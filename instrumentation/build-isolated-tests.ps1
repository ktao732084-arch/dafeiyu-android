param([string]$Sdk,[string]$Jdk,[string]$KeyStore)
$ErrorActionPreference='Stop'
$root=Split-Path $PSScriptRoot
$out=Join-Path $PSScriptRoot 'build/isolated'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
$bt=Join-Path $Sdk 'build-tools/36.0.0';$jar=Join-Path $Sdk 'platforms/android-34/android.jar'
Run "$Jdk/bin/javac.exe" @('-encoding','UTF-8','--release','8','-cp',"$jar;$root/build/classes",'-d',"$out/classes","$PSScriptRoot/AgentInstrumentation.java")
$classes=@(Get-ChildItem "$root/build/classes","$out/classes" -Recurse -Filter *.class | ForEach-Object FullName)
$d8=@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$jar,'--min-api','26','--output',"$out/dex")+$classes
[IO.File]::WriteAllLines("$out/d8.args",($d8 | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),[Text.UTF8Encoding]::new($false))
Run "$Jdk/bin/java.exe" @("@$out/d8.args")
# A separate UID keeps its Android Keystore, preferences, files and lifecycle away from the user's app.
$manifest=@'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.dafeiyu.compatchecks">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>
 <application android:label="Dafeiyu isolated checks" android:theme="@style/AppTheme" android:allowBackup="false"/>
 <instrumentation android:name="com.xiaoxiao.pet.AgentInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
</manifest>
'@
[IO.File]::WriteAllText("$out/AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
Run "$bt/aapt.exe" @('package','-f','-M',"$out/AndroidManifest.xml",'-I',$jar,'-A',"$root/fish-assets",'-S',"$root/res",'-0','mp4','-F',"$out/unsigned.apk")
Push-Location "$out/dex"
try{Run "$bt/aapt.exe" @('add',"$out/unsigned.apk",'classes.dex')}finally{Pop-Location}
Run "$bt/zipalign.exe" @('-f','4',"$out/unsigned.apk","$out/aligned.apk")
$env:JAVA_HOME=$Jdk
Run "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"pass:$env:XIAOXIAO_STORE_PASS",'--out',"$out/isolated-checks.apk","$out/aligned.apk")
