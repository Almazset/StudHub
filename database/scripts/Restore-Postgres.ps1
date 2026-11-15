[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupDirectory,
    [Parameter(Mandatory=$true)][string]$TargetDatabase,
    [string]$Container = 'studhub-db',
    [string]$User = 'studhub'
)
. "$PSScriptRoot/Recovery.Common.ps1"
# Validate the target BEFORE any connection or database modification.
Assert-Identifier $TargetDatabase
if ($TargetDatabase -cnotmatch '^studhub_restore_[a-z0-9_]+$') { throw 'Use a new database named studhub_restore_... . Existing working databases are never overwritten.' }
Assert-Identifier $User
$manifest = Read-VerifiedBackup $BackupDirectory
if ($TargetDatabase -eq $manifest.database) { throw 'Source and target databases must be different.' }
$script:Docker = Find-Docker
Assert-Container $Container
$version = (Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', 'postgres', '-Atqc', 'SHOW server_version_num;')).Trim()
if ([int]$version -lt 160000 -or [int]$version -ge 170000) { throw 'Restore target must use PostgreSQL 16.' }
$exists = Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', 'postgres', '-Atqc', "SELECT count(*) FROM pg_database WHERE datname = '$TargetDatabase';")
if ($exists -ne '0') { throw 'Target database already exists. Choose a new name; nothing was overwritten.' }
$temporaryDirectory = '/tmp/studhub-restore-' + [guid]::NewGuid().ToString('N')
Invoke-DockerChecked @('exec', $Container, 'mkdir', '-m', '700', '--', $temporaryDirectory) | Out-Null
$temporary = $temporaryDirectory + '/database.dump'
try {
    Invoke-DockerChecked @('cp', (Join-Path (Resolve-Path -LiteralPath $BackupDirectory).Path 'database.dump'), "${Container}:$temporary") | Out-Null
    Invoke-DockerChecked @('exec', $Container, 'pg_restore', '--list', $temporary) | Out-Null
    # createdb fails on races too; do not restore until creation succeeds.
    Invoke-DockerChecked @('exec', $Container, 'createdb', '-w', '-U', $User, '--template=template0', '--owner', $User, $TargetDatabase) | Out-Null
    Invoke-DockerChecked @('exec', $Container, 'pg_restore', '-w', '-U', $User, '--dbname', $TargetDatabase, '--single-transaction', '--exit-on-error', '--no-owner', '--no-acl', $temporary) | Out-Null
    Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', $TargetDatabase, '-v', 'ON_ERROR_STOP=1', '-qc', 'ANALYZE;') | Out-Null
    Write-Output "Restored into $TargetDatabase. Application configuration and working database were not changed."
} finally {
    & $script:Docker exec $Container rm -f -- $temporary | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove temporary dump $temporary from $Container." }
    & $script:Docker exec $Container rmdir -- $temporaryDirectory | Out-Null
}
