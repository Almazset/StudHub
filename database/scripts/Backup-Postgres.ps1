[CmdletBinding()]
param(
    [string]$Container = 'studhub-db',
    [string]$Database = 'studhub',
    [string]$User = 'studhub',
    [string]$OutputRoot = (Join-Path $env:LOCALAPPDATA 'StudHub\backups'),
    [string]$KeyFile = ''
)
. "$PSScriptRoot/Recovery.Common.ps1"
$script:Docker = Find-Docker
Assert-Identifier $Database
Assert-Identifier $User
Assert-Container $Container
$version = (Invoke-DockerChecked @('exec', $Container, 'psql', '-X', '-w', '-U', $User, '-d', $Database, '-Atqc', 'SHOW server_version_num;')).Trim()
if ([int]$version -lt 160000 -or [int]$version -ge 170000) { throw 'These scripts are verified for PostgreSQL 16. Check compatibility before using another major version.' }

# Match SecretVault precedence; never dump .env or API credentials into the backup.
if ($KeyFile) { $keyBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $KeyFile).Path) }
elseif ($env:STUDHUB_2FA_KEY) { $keyBytes = [Convert]::FromBase64String($env:STUDHUB_2FA_KEY) }
else {
    $keyPath = $env:STUDHUB_2FA_KEY_FILE
    if (-not $keyPath) { $keyPath = Join-Path $PSScriptRoot '../../.studhub/security.key' }
    if (-not (Test-Path -LiteralPath $keyPath)) { throw 'Two-factor encryption key not found. Supply the actual application key with -KeyFile or STUDHUB_2FA_KEY. Do not generate a replacement.' }
    $keyBytes = [IO.File]::ReadAllBytes((Resolve-Path -LiteralPath $keyPath).Path)
}
if ($keyBytes.Length -ne 32) { throw 'The application encryption key must contain 32 bytes.' }
$id = [guid]::NewGuid().ToString('N')
$directory = Join-Path ([IO.Path]::GetFullPath($OutputRoot)) ("studhub-{0}-{1}" -f [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssZ'), $id.Substring(0,8))
New-PrivateDirectory $directory
$temporaryDirectory = "/tmp/studhub-backup-$id"
Invoke-DockerChecked @('exec', $Container, 'mkdir', '-m', '700', '--', $temporaryDirectory) | Out-Null
$temporary = "$temporaryDirectory/database.dump"
try {
    # No PowerShell binary redirection: pg_dump writes inside Docker, docker cp preserves bytes.
    Invoke-DockerChecked @('exec', $Container, 'pg_dump', '-w', '-U', $User, '-d', $Database, '--format=custom', '--no-owner', '--no-acl', '--file', $temporary) | Out-Null
    Invoke-DockerChecked @('exec', $Container, 'pg_restore', '--list', $temporary) | Out-Null
    $partial = Join-Path $directory 'database.dump.partial'
    Invoke-DockerChecked @('cp', "${Container}:$temporary", $partial) | Out-Null
    if ((Get-Item -LiteralPath $partial).Length -lt 5) { throw 'Dump is empty.' }
    Move-Item -LiteralPath $partial -Destination (Join-Path $directory 'database.dump')
    [IO.File]::WriteAllBytes((Join-Path $directory 'two-factor.key'), $keyBytes)
    $manifest = [ordered]@{
        formatVersion=1; status='complete'; createdUtc=[DateTime]::UtcNow.ToString('o')
        database=$Database; postgresVersionNumber=[int]$version
        dumpSha256=(Get-FileHash -LiteralPath (Join-Path $directory 'database.dump') -Algorithm SHA256).Hash
        keySha256=(Get-FileHash -LiteralPath (Join-Path $directory 'two-factor.key') -Algorithm SHA256).Hash
        encrypted=$false; keyIncluded=$true
    }
    # Completion marker is published last. Failed/partial archives never appear complete.
    $manifest | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $directory 'manifest.json.partial') -Encoding UTF8
    Move-Item -LiteralPath (Join-Path $directory 'manifest.json.partial') -Destination (Join-Path $directory 'manifest.json')
    Read-VerifiedBackup $directory | Out-Null
    Write-Output $directory
} finally {
    if ($keyBytes) { [Array]::Clear($keyBytes, 0, $keyBytes.Length) }
    # The only container file removed is the unique temporary dump created by this invocation.
    & $script:Docker exec $Container rm -f -- $temporary | Out-Null
    if ($LASTEXITCODE -ne 0) { Write-Warning "Could not remove temporary dump $temporary from $Container." }
    & $script:Docker exec $Container rmdir -- $temporaryDirectory | Out-Null
}
