[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupDirectory,
    [Parameter(Mandatory=$true)][string]$ExistingTestDatabase,
    [string]$Container='studhub-db', [string]$User='studhub'
)
. "$PSScriptRoot/../scripts/Recovery.Common.ps1"
$script:Docker=Find-Docker
Assert-Identifier $User
Assert-Container $Container
if ($ExistingTestDatabase -cnotmatch '^studhub_restore_[a-z0-9_]+$') { throw 'Supply the database produced by Test-PostgresRestore.' }
$restore=Join-Path $PSScriptRoot '../scripts/Restore-Postgres.ps1'
$original=Read-VerifiedBackup $BackupDirectory
$scratch=Join-Path $env:LOCALAPPDATA ('StudHub/recovery-guard-' + [guid]::NewGuid().ToString('N'))
New-PrivateDirectory $scratch
$target='studhub_restore_guard_' + [guid]::NewGuid().ToString('N').Substring(0,12)
function Assert-Refused([string]$Name, [scriptblock]$Action) {
    $refused=$false
    try { & $Action | Out-Null } catch { $refused=$true }
    if (-not $refused) { throw "Unsafe operation was not rejected: $Name" }
    Write-Output "PASS: $Name"
}
function Reset-Fixture {
    foreach ($file in @('database.dump','two-factor.key','manifest.json')) {
        Copy-Item -LiteralPath (Join-Path $BackupDirectory $file) -Destination (Join-Path $scratch $file) -Force
    }
}
$before=@(Invoke-DockerChecked @('exec',$Container,'psql','-X','-w','-U',$User,'-d','postgres','-Atqc','SELECT datname FROM pg_database ORDER BY datname;'))
try {
    Assert-Refused 'Working database cannot be overwritten' { & $restore -BackupDirectory $BackupDirectory -TargetDatabase 'studhub' -Container $Container -User $User }
    Assert-Refused 'SQL identifiers are validated' { & $restore -BackupDirectory $BackupDirectory -TargetDatabase "studhub_restore_x';DROP DATABASE studhub;--" -Container $Container -User $User }
    Assert-Refused 'Existing test database is preserved' { & $restore -BackupDirectory $BackupDirectory -TargetDatabase $ExistingTestDatabase -Container $Container -User $User }
    Reset-Fixture
    [IO.File]::WriteAllText((Join-Path $scratch 'database.dump'),'corrupted')
    Assert-Refused 'Damaged dump checksum fails before database creation' { & $restore -BackupDirectory $scratch -TargetDatabase $target -Container $Container -User $User }
    Reset-Fixture
    [IO.File]::WriteAllBytes((Join-Path $scratch 'two-factor.key'),(New-Object byte[] 32))
    Assert-Refused 'Wrong two-factor key checksum is rejected' { Read-VerifiedBackup $scratch }
    Reset-Fixture
    Remove-Item -LiteralPath (Join-Path $scratch 'two-factor.key')
    Assert-Refused 'Missing key is rejected' { Read-VerifiedBackup $scratch }
    Reset-Fixture
    Remove-Item -LiteralPath (Join-Path $scratch 'manifest.json')
    Assert-Refused 'Incomplete archive is rejected' { Read-VerifiedBackup $scratch }
    Reset-Fixture
    [IO.File]::WriteAllText((Join-Path $scratch 'database.dump'),'not-a-postgres-archive')
    $manifest=Get-Content -LiteralPath (Join-Path $scratch 'manifest.json') -Raw | ConvertFrom-Json
    $manifest.dumpSha256=(Get-FileHash -LiteralPath (Join-Path $scratch 'database.dump') -Algorithm SHA256).Hash
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $scratch 'manifest.json') -Encoding UTF8
    Assert-Refused 'Invalid archive format is rejected even with a matching checksum' { & $restore -BackupDirectory $scratch -TargetDatabase $target -Container $Container -User $User }
    $after=@(Invoke-DockerChecked @('exec',$Container,'psql','-X','-w','-U',$User,'-d','postgres','-Atqc','SELECT datname FROM pg_database ORDER BY datname;'))
    if (@(Compare-Object $before $after).Count -ne 0) { throw 'Database list changed during rejected operations.' }
    Read-VerifiedBackup $BackupDirectory | Out-Null
    Write-Output 'PASS: database list and original backup unchanged'
} finally {
    # Delete only the three known files in the freshly created fixture directory; never recurse.
    $root=[IO.Path]::GetFullPath($scratch).TrimEnd('\') + '\'
    foreach ($name in @('database.dump','two-factor.key','manifest.json')) {
        $file=[IO.Path]::GetFullPath((Join-Path $scratch $name))
        if (-not $file.StartsWith($root,[StringComparison]::OrdinalIgnoreCase)) { throw 'Cleanup path outside fixture.' }
        if (Test-Path -LiteralPath $file) { Remove-Item -LiteralPath $file }
    }
    Remove-Item -LiteralPath $scratch
}
