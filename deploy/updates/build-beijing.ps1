param([Parameter(Mandatory=$true)][string]$Sdk,
      [Parameter(Mandatory=$true)][string]$Jdk,
      [Parameter(Mandatory=$true)][string]$KeyStore)
$ErrorActionPreference='Stop'
$ProjectRoot=Split-Path (Split-Path $PSScriptRoot)
& (Join-Path $ProjectRoot 'build.ps1') -Sdk $Sdk -Jdk $Jdk -KeyStore $KeyStore -UpdateUrl 'https://81.70.79.133/dafeiyu-updates/stable.json'
