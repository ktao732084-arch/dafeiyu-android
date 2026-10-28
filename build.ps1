param([string]$Sdk='',[string]$Jdk='',[string]$KeyStore='')
$ErrorActionPreference='Stop'
$Root=$PSScriptRoot
if(-not $Sdk){$Sdk=if($env:ANDROID_HOME){$env:ANDROID_HOME}else{Join-Path $env:USERPROFILE 'diauto-android-sdk'}}
$LocalTools=Join-Path (Split-Path $Root) 'android-lite\tools'
if(-not $Jdk){if(Test-Path "$LocalTools\jdk"){$Jdk=(Get-ChildItem "$LocalTools\jdk" -Directory | Select-Object -First 1).FullName}else{$Jdk=$env:JAVA_HOME}}
if(-not $KeyStore){$KeyStore=Join-Path $LocalTools 'test.keystore'}
$StorePass=if($env:XIAOXIAO_STORE_PASS){$env:XIAOXIAO_STORE_PASS}elseif($KeyStore -eq (Join-Path $LocalTools 'test.keystore')){'android'}else{throw 'Set XIAOXIAO_STORE_PASS for your signing key.'}
$Sdk=[IO.Path]::GetFullPath($Sdk)
$Jdk=[IO.Path]::GetFullPath($Jdk)
$KeyStore=[IO.Path]::GetFullPath($KeyStore)
$Bt="$Sdk\build-tools\36.0.0"
$Jar="$Sdk\platforms\android-34\android.jar"
foreach($Required in @("$Jdk\bin\javac.exe",$Jar,"$Bt\aapt.exe",$KeyStore)){if(-not(Test-Path -LiteralPath $Required)){throw "Required build file missing: $Required. See BUILD.md."}}
function RunChecked($Exe,$Arguments){ & $Exe @Arguments; if($LASTEXITCODE -ne 0){throw "Failed: $Exe ($LASTEXITCODE)"} }
New-Item -ItemType Directory -Force "$Root\fish-assets" | Out-Null
Copy-Item -LiteralPath "$Root\assets\main" -Destination "$Root\fish-assets" -Recurse -Force
Copy-Item -LiteralPath "$Root\assets\catalog.json","$Root\assets\persona-default.txt","$Root\assets\personas.json" -Destination "$Root\fish-assets" -Force
New-Item -ItemType Directory -Force "$Root\build\classes","$Root\build\dex","$Root\build\generated" | Out-Null
$ClassesDir=[IO.Path]::GetFullPath((Join-Path $Root 'build\classes'))
if(-not $ClassesDir.StartsWith([IO.Path]::GetFullPath($Root)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Invalid class output directory'}
Get-ChildItem -LiteralPath $ClassesDir -Recurse -Filter *.class | ForEach-Object {Remove-Item -LiteralPath $_.FullName}
RunChecked "$Bt\aapt.exe" @('package','-f','-m','-J',"$Root\build\generated",'-M',"$Root\AndroidManifest.xml",'-I',$Jar,'-S',"$Root\res")
$sources=@(Get-ChildItem "$Root\src","$Root\build\generated" -Recurse -Filter *.java | ForEach-Object FullName)
RunChecked "$Jdk\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-classpath',$Jar,'-d',"$Root\build\classes")+$sources)
$classes=@(Get-ChildItem "$Root\build\classes" -Recurse -Filter *.class | ForEach-Object FullName)
$env:JAVA_HOME=$Jdk
$D8Args=@('-cp',"$Bt\lib\d8.jar",'com.android.tools.r8.D8','--lib',$Jar,'--min-api','26','--output',"$Root\build\dex")+$classes
$ArgFile=Join-Path $Root 'build\d8.args'
[IO.File]::WriteAllLines($ArgFile,($D8Args | ForEach-Object {'"'+$_.Replace('\','/')+'"'}),[Text.UTF8Encoding]::new($false))
RunChecked "$Jdk\bin\java.exe" @("@$ArgFile")
RunChecked "$Bt\aapt.exe" @('package','-f','-M',"$Root\AndroidManifest.xml",'-I',$Jar,'-A',"$Root\fish-assets",'-S',"$Root\res",'-0','mp4','-F',"$Root\build\unsigned.apk",'--version-code','6','--version-name','0.4.2')
Push-Location "$Root\build\dex"
try {RunChecked "$Bt\aapt.exe" @('add',"$Root\build\unsigned.apk",'classes.dex')} finally {Pop-Location}
RunChecked "$Bt\zipalign.exe" @('-f','4',"$Root\build\unsigned.apk", "$Root\build\aligned.apk")
RunChecked "$Bt\apksigner.bat" @('sign','--ks',$KeyStore,'--ks-pass',"pass:$StorePass",'--out',"$Root\build\dafeiyu-0.4.2.apk", "$Root\build\aligned.apk")
RunChecked "$Bt\apksigner.bat" @('verify','--verbose',"$Root\build\dafeiyu-0.4.2.apk")
