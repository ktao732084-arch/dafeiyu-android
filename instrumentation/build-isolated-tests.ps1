param([string]$Sdk,[string]$Jdk,[string]$KeyStore,[string]$TestPackage='com.dafeiyu.compatchecks')
$ErrorActionPreference='Stop'
if($TestPackage -notmatch '^com\.dafeiyu\.[a-z]+checks$'){throw 'Use a dedicated com.dafeiyu.*checks test package, never the production app.'}
$root=Split-Path $PSScriptRoot
$out=Join-Path $PSScriptRoot 'build/isolated'
New-Item -ItemType Directory -Force "$out/classes","$out/dex" | Out-Null
function Run($Exe,$Arguments){& $Exe @Arguments;if($LASTEXITCODE -ne 0){throw "Failed: $Exe"}}
$bt=Join-Path $Sdk 'build-tools/36.0.0';$jar=Join-Path $Sdk 'platforms/android-34/android.jar'
Run "$Jdk/bin/javac.exe" @('-encoding','UTF-8','--release','8','-cp',"$jar;$root/build/classes",'-d',"$out/classes","$PSScriptRoot/AgentInstrumentation.java","$PSScriptRoot/UpdateInstrumentationChecks.java","$PSScriptRoot/UpdateNetworkInstrumentation.java","$PSScriptRoot/TouchReplyActivity.java","$PSScriptRoot/TouchReplyInstrumentation.java","$PSScriptRoot/ChatPresentationInstrumentation.java","$PSScriptRoot/ExperienceNotebookActivity.java","$PSScriptRoot/ExperienceInstrumentation.java")
$classes=@(Get-ChildItem "$root/build/classes","$out/classes" -Recurse -Filter *.class | ForEach-Object FullName)
$d8=@('-cp',"$bt/lib/d8.jar",'com.android.tools.r8.D8','--lib',$jar,'--min-api','26','--output',"$out/dex")+$classes
[IO.File]::WriteAllLines("$out/d8.args",($d8 | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),[Text.UTF8Encoding]::new($false))
Run "$Jdk/bin/java.exe" @("@$out/d8.args")
# A separate UID keeps its Android Keystore, preferences, files and lifecycle away from the user's app.
$manifest=@'
<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="com.dafeiyu.compatchecks">
 <uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/>
 <uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES"/>
 <uses-permission android:name="android.permission.INTERNET"/>
 <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW"/>
 <uses-permission android:name="android.permission.FOREGROUND_SERVICE"/>
 <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE"/>
 <uses-permission android:name="android.permission.POST_NOTIFICATIONS"/>
 <application android:label="Dafeiyu isolated checks" android:theme="@style/AppTheme" android:allowBackup="false">
  <provider android:name="com.xiaoxiao.pet.UpdateFileProvider" android:authorities="com.dafeiyu.compatchecks.updates" android:exported="false" android:grantUriPermissions="true"/>
  <activity android:name="com.xiaoxiao.pet.TouchReplyActivity" android:screenOrientation="portrait" android:configChanges="orientation|screenSize" android:exported="false"/>
  <activity android:name="com.xiaoxiao.pet.ExperienceNotebookActivity" android:screenOrientation="portrait" android:exported="false"/>
  <service android:name="com.xiaoxiao.pet.PetService" android:exported="false" android:foregroundServiceType="specialUse"><property android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE" android:value="Isolated touch reply verification"/></service>
 </application>
 <instrumentation android:name="com.xiaoxiao.pet.AgentInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
 <instrumentation android:name="com.xiaoxiao.pet.UpdateNetworkInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
 <instrumentation android:name="com.xiaoxiao.pet.TouchReplyInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
 <instrumentation android:name="com.xiaoxiao.pet.ChatPresentationInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
 <instrumentation android:name="com.xiaoxiao.pet.ExperienceInstrumentation" android:targetPackage="com.dafeiyu.compatchecks" android:functionalTest="true"/>
</manifest>
'@
$manifest=$manifest.Replace('com.dafeiyu.compatchecks',$TestPackage)
[IO.File]::WriteAllText("$out/AndroidManifest.xml",$manifest,[Text.UTF8Encoding]::new($false))
New-Item -ItemType Directory -Force "$out/fixture","$out/add/assets" | Out-Null
$fixture='<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="'+$TestPackage+'"><uses-sdk android:minSdkVersion="26" android:targetSdkVersion="34"/><application android:label="Update verification fixture" android:hasCode="false"/></manifest>'
[IO.File]::WriteAllText("$out/fixture/AndroidManifest.xml",$fixture,[Text.UTF8Encoding]::new($false))
Run "$bt/aapt.exe" @('package','-f','-M',"$out/fixture/AndroidManifest.xml",'-I',$jar,'-F',"$out/fixture/unsigned.apk",'--version-code','1','--version-name','test-next')
Run "$bt/zipalign.exe" @('-f','4',"$out/fixture/unsigned.apk","$out/fixture/aligned.apk")
$env:JAVA_HOME=$Jdk
Run "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"pass:$env:XIAOXIAO_STORE_PASS",'--out',"$out/add/assets/update-fixture.apk","$out/fixture/aligned.apk")
Run "$bt/aapt.exe" @('package','-f','-M',"$out/AndroidManifest.xml",'-I',$jar,'-A',"$root/fish-assets",'-S',"$root/res",'-0','mp4','-F',"$out/unsigned.apk")
Push-Location "$out/add"
try{Run "$bt/aapt.exe" @('add',"$out/unsigned.apk",'assets/update-fixture.apk')}finally{Pop-Location}
Push-Location "$out/dex"
try{Run "$bt/aapt.exe" @('add',"$out/unsigned.apk",'classes.dex')}finally{Pop-Location}
Run "$bt/zipalign.exe" @('-f','4',"$out/unsigned.apk","$out/aligned.apk")
$env:JAVA_HOME=$Jdk
Run "$bt/apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"pass:$env:XIAOXIAO_STORE_PASS",'--out',"$out/isolated-checks.apk","$out/aligned.apk")
