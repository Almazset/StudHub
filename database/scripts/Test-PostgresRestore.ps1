[CmdletBinding()]
param(
    [Parameter(Mandatory=$true)][string]$BackupDirectory,
    [string]$Container = 'studhub-db',
    [string]$User = 'studhub',
    [switch]$CompareWithStoppedSource
)
. "$PSScriptRoot/Recovery.Common.ps1"
$manifest = Read-VerifiedBackup $BackupDirectory
$script:Docker = Find-Docker
Assert-Identifier $User
Assert-Container $Container
$target = 'studhub_restore_test_' + [guid]::NewGuid().ToString('N').Substring(0,12)
& "$PSScriptRoot/Restore-Postgres.ps1" -BackupDirectory $BackupDirectory -TargetDatabase $target -Container $Container -User $User
$temporary = '/tmp/studhub-fingerprint-' + [guid]::NewGuid().ToString('N') + '.sql'
try {
    Invoke-DockerChecked @('cp', (Join-Path $PSScriptRoot 'fingerprint.sql'), "${Container}:$temporary") | Out-Null
    $restored = @(Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', $target, '-v', 'ON_ERROR_STOP=1', '-Atqf', $temporary))
    $tables = @($restored | Where-Object { $_ -like 'table:*' })
    if ($tables.Count -eq 0) { throw 'Restore contains no application tables.' }
    $compared = $false
    if ($CompareWithStoppedSource) {
        # Caller must stop application/other writers BEFORE creating the backup.
        $source = @(Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', $manifest.database, '-v', 'ON_ERROR_STOP=1', '-Atqf', $temporary))
        if (@(Compare-Object $source $restored).Count -ne 0) { throw 'Source and restore differ. Check that all writers were stopped before backup; test DB is preserved for investigation.' }
        $compared = $true
    }
    $report = [ordered]@{ checkedUtc=[DateTime]::UtcNow.ToString('o'); targetDatabase=$target; restored=$true; sourceCompared=$compared; tables=$tables.Count; fingerprints=$restored }
    $reportPath = Join-Path (Resolve-Path -LiteralPath $BackupDirectory).Path ("restore-check-$target.json")
    $report | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath $reportPath -Encoding UTF8
    Write-Output "Verified $($tables.Count) tables. Source comparison: $compared. Test database kept: $target"
    Write-Output "Report: $reportPath"
} finally {
    & $script:Docker exec $Container rm -f -- $temporary | Out-Null
}
