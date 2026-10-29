param([Parameter(Mandatory=$true)][string]$Apk,
      [Parameter(Mandatory=$true)][string]$Notes,
      [string]$Commit='HEAD',
      [string]$LocalConfig='',
      [switch]$Publish,
      [switch]$VerifyOnly)
$ErrorActionPreference='Stop'
if($Publish -and $VerifyOnly){throw 'Choose Publish or VerifyOnly, not both.'}
$arguments=@((Join-Path $PSScriptRoot 'tools/release-sync.py'),'--apk',$Apk,'--notes',$Notes,'--commit',$Commit)
if($LocalConfig){$arguments+=@('--local-config',$LocalConfig)}
if($Publish){$arguments+='--publish'}elseif($VerifyOnly){$arguments+='--verify-only'}
& python @arguments
if($LASTEXITCODE -ne 0){throw 'Release sync did not finish. Fix the reported error and rerun the same command.'}
