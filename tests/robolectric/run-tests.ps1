param([Parameter(Mandatory=$true)][string]$AndroidRoot,[Parameter(Mandatory=$true)][string]$Sdk,[Parameter(Mandatory=$true)][string]$Jdk,[string]$Maven='')
$ErrorActionPreference='Stop'
$root=$PSScriptRoot
$AndroidRoot=(Resolve-Path -LiteralPath $AndroidRoot).Path.Replace('\','/')
$Sdk=(Resolve-Path -LiteralPath $Sdk).Path.Replace('\','/')
$Jdk=(Resolve-Path -LiteralPath $Jdk).Path
if(!(Test-Path -LiteralPath "$AndroidRoot/build/unsigned.apk")){throw 'Build the product first; binary resources and matching compiled classes are required.'}
if($Maven){$Maven=(Resolve-Path -LiteralPath $Maven).Path;python "$root/bootstrap.py" --skip-maven}else{python "$root/bootstrap.py";$Maven=Join-Path $root 'apache-maven-3.9.9/bin/mvn.cmd'}
if($LASTEXITCODE -ne 0){throw 'Dependency bootstrap failed'}
& "$Jdk/bin/jar.exe" cf "$root/libs/app.jar" -C "$AndroidRoot/build/classes" .
if($LASTEXITCODE -ne 0){throw 'Cannot snapshot app classes'}
New-Item -ItemType Directory -Force -Path "$root/src/test/resources/com/android/tools" | Out-Null
@"
android_merged_manifest=$AndroidRoot/AndroidManifest.xml
android_custom_package=com.dafeiyu.companion
android_resource_apk=$AndroidRoot/build/unsigned.apk
"@ | Set-Content -LiteralPath "$root/src/test/resources/com/android/tools/test_config.properties" -Encoding ascii
$repository=(Join-Path $root 'repository').Replace('\','/')
@"
<settings><localRepository>$repository</localRepository><mirrors><mirror><id>central-alias</id><url>https://repo1.maven.org/maven2</url><mirrorOf>central</mirrorOf></mirror></mirrors></settings>
"@ | Set-Content -LiteralPath "$root/settings.xml" -Encoding ascii
$env:JAVA_HOME=$Jdk
Push-Location -LiteralPath $root
try {& $Maven -B -s settings.xml "-Dandroid.sdk=$Sdk" test;if($LASTEXITCODE -ne 0){throw 'Robolectric checks failed; inspect target/surefire-reports'}}finally{Pop-Location}
