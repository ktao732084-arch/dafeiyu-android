param(
 [ValidateSet('list','reply','close','backup')][string]$Action='list',
 [string]$Receipt='',
 [string]$ReplyFile='',
 [string]$RequestId='',
 [string]$Config=(Join-Path $PSScriptRoot '../updates/release.local.json')
)
$ErrorActionPreference='Stop'
$settings=Get-Content -LiteralPath $Config -Raw -Encoding UTF8 | ConvertFrom-Json
if($Receipt -and $Receipt -notmatch '^[A-Za-z0-9_-]{24}$'){throw 'Invalid receipt'}
if($Action -in @('reply','close') -and -not $Receipt){throw 'Receipt is required'}
$command='set -a; . /etc/dafeiyu-feedback.env; set +a; runuser -u dafeiyu-feedback -- /opt/dafeiyu-feedback/venv/bin/python /opt/dafeiyu-feedback/feedback_server.py '+$Action
if($Receipt){$command+=' --id '+$Receipt}
if($Action -eq 'reply'){
 if(-not $ReplyFile){throw 'ReplyFile is required'}
 $body=Get-Content -LiteralPath $ReplyFile -Raw -Encoding UTF8
 if(-not $RequestId){$RequestId=[guid]::NewGuid().ToString()}
 if($RequestId -notmatch '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'){throw 'RequestId must be a UUID'}
 $command+=' --file - --request-id '+$RequestId
 Write-Host "Reply request id: $RequestId (reuse for a retry)"
 $body | ssh -o BatchMode=yes -i $settings.ssh_identity $settings.ssh_target $command
}elseif($Action -eq 'backup'){
 ssh -o BatchMode=yes -i $settings.ssh_identity $settings.ssh_target 'systemctl start dafeiyu-feedback-backup.service'
}else{
 ssh -o BatchMode=yes -i $settings.ssh_identity $settings.ssh_target $command
}
if($LASTEXITCODE -ne 0){throw 'Feedback management command failed'}
