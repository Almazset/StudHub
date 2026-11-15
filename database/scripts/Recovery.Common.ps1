Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

function Find-Docker {
    $command = Get-Command docker -ErrorAction SilentlyContinue
    if ($command) { return $command.Source }
    $candidate = Join-Path $env:LOCALAPPDATA 'Programs\DockerDesktop\resources\bin\docker.exe'
    if (Test-Path -LiteralPath $candidate) { return $candidate }
    throw 'Docker CLI not found. Install/start Docker Desktop first.'
}
function Invoke-DockerChecked {
    param([string[]]$DockerArguments)
    $result = & $script:Docker @DockerArguments
    if ($LASTEXITCODE -ne 0) { throw "Docker/PostgreSQL command failed (exit $LASTEXITCODE). No successful backup/restore is reported." }
    return $result
}
function Assert-Identifier {
    param([string]$Value)
    if ($Value -cnotmatch '^[a-z][a-z0-9_]{0,62}$') { throw 'Database/user name must contain lowercase ASCII letters, digits and underscores (max 63 characters).' }
}
function Assert-Container {
    param([string]$Name)
    if ($Name -notmatch '^[a-zA-Z0-9][a-zA-Z0-9_.-]*$') { throw 'Invalid container name.' }
    $state = Invoke-DockerChecked @('inspect', '--format', '{{.State.Running}}', $Name)
    if ($state -ne 'true') { throw "Container $Name is stopped. Start Docker Desktop and run: docker start $Name" }
}
function New-PrivateDirectory {
    param([string]$Path)
    # Only newly created dedicated backup directories have their ACL changed.
    if (Test-Path -LiteralPath $Path) { throw 'Backup directory already exists; refusing to replace it.' }
    $directory = New-Item -ItemType Directory -Path $Path
    $acl = New-Object System.Security.AccessControl.DirectorySecurity
    $sid = [System.Security.Principal.WindowsIdentity]::GetCurrent().User
    $acl.SetOwner($sid)
    $acl.SetAccessRuleProtection($true, $false)
    foreach ($identity in @($sid, [System.Security.Principal.SecurityIdentifier]::new('S-1-5-18'))) {
        $rule = [System.Security.AccessControl.FileSystemAccessRule]::new($identity, 'FullControl', 'ContainerInherit,ObjectInherit', 'None', 'Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $directory.FullName -AclObject $acl
}
function Read-VerifiedBackup {
    param([string]$Directory)
    $directoryPath = (Resolve-Path -LiteralPath $Directory).Path
    $manifestPath = Join-Path $directoryPath 'manifest.json'
    $manifest = Get-Content -LiteralPath $manifestPath -Raw | ConvertFrom-Json
    if ($manifest.formatVersion -ne 1 -or $manifest.status -ne 'complete') { throw 'Unsupported or incomplete backup.' }
    Assert-Identifier $manifest.database
    foreach ($entry in @(@('database.dump', $manifest.dumpSha256), @('two-factor.key', $manifest.keySha256))) {
        if ($entry[1] -notmatch '^[a-fA-F0-9]{64}$') { throw 'Invalid backup checksum metadata.' }
        $file = Join-Path $directoryPath $entry[0]
        if ((Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash -ne $entry[1]) { throw "Checksum mismatch: $($entry[0]). Restore refused." }
    }
    if ((Get-Item -LiteralPath (Join-Path $directoryPath 'two-factor.key')).Length -ne 32) { throw 'Invalid two-factor key length.' }
    return $manifest
}
