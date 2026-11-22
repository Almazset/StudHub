[CmdletBinding()]
param(
    [string]$Container = 'studhub-db',
    [string]$Database = 'studhub',
    [string]$User = 'studhub'
)

. "$PSScriptRoot/Recovery.Common.ps1"

$script:Docker = Find-Docker
Assert-Identifier $Database
Assert-Identifier $User
Assert-Container $Container

function Read-Schema([string]$DatabaseName, [string]$Sql) {
    $result = $Sql | & $script:Docker exec -i $Container psql -X -w -q -A -t -v ON_ERROR_STOP=1 -U $User -d $DatabaseName
    if ($LASTEXITCODE -ne 0) { throw "Could not read the PostgreSQL schema of $DatabaseName." }
    return $result
}

$history = Read-Schema $Database "select to_regclass('public.flyway_schema_history') is not null;"
if ($history -eq 't') {
    throw 'This database already has Flyway history. Start StudHub normally to validate it.'
}

# Compare definitions rather than generated constraint/index names or ordinal position.
$catalogSql = @"
SET search_path = pg_catalog, public;
WITH tables AS (
    SELECT relation.oid, relation.relname
      FROM pg_class relation
      JOIN pg_namespace ns ON ns.oid = relation.relnamespace
     WHERE ns.nspname = 'public' AND relation.relkind IN ('r', 'p')
)
SELECT jsonb_build_array('table', t.relname)::text FROM tables t
UNION
SELECT jsonb_build_array('column', t.relname, a.attname, format_type(a.atttypid, a.atttypmod),
                         a.attnotnull, a.attidentity, a.attgenerated,
                         pg_get_expr(d.adbin, d.adrelid))::text
  FROM tables t
  JOIN pg_attribute a ON a.attrelid = t.oid AND a.attnum > 0 AND NOT a.attisdropped
  LEFT JOIN pg_attrdef d ON d.adrelid = t.oid AND d.adnum = a.attnum
UNION
SELECT jsonb_build_array('constraint', t.relname,
       -- A dump and restore can rewrite implicit casts for this exact V011 enum check.
       CASE WHEN t.relname = 'news_posts' AND c.contype = 'c'
                 AND regexp_replace(pg_get_constraintdef(c.oid),
                       '(::character varying|::text(\[\])?|[[:space:]()])', '', 'g')
                     = 'CHECKstatus=ANYARRAY[''PLANNED'',''IN_PROGRESS'',''RELEASED'']'
            THEN 'CHECK status IN (PLANNED, IN_PROGRESS, RELEASED)'
            ELSE pg_get_constraintdef(c.oid) END, c.convalidated)::text
  FROM tables t
  JOIN pg_constraint c ON c.conrelid = t.oid
UNION
SELECT jsonb_build_array('index', t.relname,
                         substring(pg_get_indexdef(i.indexrelid) FROM ' USING .*'),
                         i.indisunique, i.indisvalid)::text
  FROM tables t
  JOIN pg_index i ON i.indrelid = t.oid
 WHERE NOT EXISTS (SELECT 1 FROM pg_constraint c WHERE c.conindid = i.indexrelid)
ORDER BY 1;
"@

$baselinePath = Join-Path $PSScriptRoot '../../src/main/resources/db/migration/V011__initial_studhub_schema.sql'
$baselineSql = Get-Content -LiteralPath $baselinePath -Raw
$referenceDb = 'studhub_schema_check_' + [guid]::NewGuid().ToString('N').Substring(0, 12)
if ($referenceDb -eq $Database) { throw 'Reference database must be distinct from the application database.' }
$created = $false
try {
    Invoke-DockerChecked @('exec', $Container, 'createdb', '-w', '-U', $User, '-T', 'template0', $referenceDb) | Out-Null
    $created = $true
    Read-Schema $referenceDb $baselineSql | Out-Null
    $reference = @(Read-Schema $referenceDb $catalogSql)
    $target = @(Read-Schema $Database $catalogSql)
    if (-not $target.Count) { throw 'The database is empty. Start StudHub normally to apply the Flyway baseline.' }
    $difference = @(Compare-Object -CaseSensitive -ReferenceObject $reference -DifferenceObject $target)
    if ($difference.Count) {
        foreach ($entry in $difference | Select-Object -First 30) {
            $origin = if ($entry.SideIndicator -eq '<=') { 'expected' } else { 'installed' }
            Write-Host ($origin + ': ' + $entry.InputObject)
        }
        throw 'Schema differs from the V011 baseline. Review the differences before enrolling.'
    }
    Write-Output "Schema of $Database matches the Flyway V011 baseline."
} finally {
    if ($created) {
        Invoke-DockerChecked @('exec', $Container, 'dropdb', '-w', '-U', $User, $referenceDb) | Out-Null
    }
}
