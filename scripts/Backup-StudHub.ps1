param(
    [string]$Container = 'studhub-db',
    [string]$Database = 'studhub',
    [string]$Username = 'studhub',
    [string]$Destination = (Join-Path $PSScriptRoot '../.studhub/backups')
)
$ErrorActionPreference = 'Stop'
$dockerCommand = Get-Command docker -ErrorAction SilentlyContinue
$dockerExe = if ($dockerCommand) { $dockerCommand.Source } else { Join-Path $env:LOCALAPPDATA 'Programs/DockerDesktop/resources/bin/docker.exe' }
if (!(Test-Path -LiteralPath $dockerExe)) { throw 'Docker CLI not found' }
New-Item -ItemType Directory -Path $Destination -Force | Out-Null
$backupName = 'studhub-' + (Get-Date -Format 'yyyyMMdd-HHmmss') + '-' + [Guid]::NewGuid().ToString('N') + '.dump'
$backupFile = Join-Path (Resolve-Path -LiteralPath $Destination).Path $backupName
$containerFile = '/tmp/' + $backupName
try {
    & $dockerExe exec $Container pg_dump --username $Username --dbname $Database --format=custom --no-owner --no-acl --file $containerFile
    if ($LASTEXITCODE -ne 0) { throw 'pg_dump failed' }
    & $dockerExe exec $Container pg_restore --list $containerFile | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Backup archive validation failed' }
    & $dockerExe cp "${Container}:$containerFile" $backupFile
    if ($LASTEXITCODE -ne 0) { throw 'Could not copy backup to host' }
    if ((Get-Item -LiteralPath $backupFile).Length -eq 0) { throw 'Empty backup' }
    $hash = Get-FileHash -LiteralPath $backupFile -Algorithm SHA256
    $hash.Hash | Set-Content -LiteralPath ($backupFile + '.sha256') -Encoding ascii
    Write-Output "Backup: $backupFile"
    Write-Output 'Archive validated. This does not replace a test restoration.'
} finally {
    & $dockerExe exec $Container rm -f -- $containerFile | Out-Null
}
