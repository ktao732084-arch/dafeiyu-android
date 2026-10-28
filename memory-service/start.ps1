param([string]$Python='python',[int]$Port=8787,[string]$Adb='',[string]$Serial='')
$ErrorActionPreference='Stop'
if($Port -lt 1 -or $Port -gt 65535){throw 'Port must be 1-65535'}
if(!$env:MEMORY_SERVICE_TOKEN){
 $env:MEMORY_SERVICE_TOKEN=& $Python -c 'import secrets; print(secrets.token_urlsafe(32))'
 if($LASTEXITCODE -ne 0){throw 'Python 3.10+ is required'}
}
if($env:MEMORY_SERVICE_TOKEN.Length -lt 32 -or $env:MEMORY_SERVICE_TOKEN -match '\s'){throw 'MEMORY_SERVICE_TOKEN must be at least 32 characters with no whitespace'}
$env:PYTHONUTF8='1'
if($Adb){
 $adbArgs=@();if($Serial){$adbArgs+=@('-s',$Serial)};$adbArgs+=@('reverse',"tcp:$Port","tcp:$Port")
 & $Adb @adbArgs
 if($LASTEXITCODE -ne 0){throw 'adb reverse failed; check USB debugging and device selection'}
}
Write-Host "Phone service URL: http://127.0.0.1:$Port (requires adb reverse over USB)"
Write-Host "Access token: $env:MEMORY_SERVICE_TOKEN"
Write-Host 'Keep this terminal open. Ctrl+C stops the service. No token is written to the repository.'
& $Python -X utf8 (Join-Path $PSScriptRoot 'upstream/src/companion_api.py') --host 127.0.0.1 --port $Port
if($LASTEXITCODE -ne 0){throw 'Memory service stopped with an error'}
