<#
.SYNOPSIS
  Provision an isolated MySQL 8.0 container (port 3307) with `gvrp_test_db`,
  clone the exact schema (DDL only, zero rows) from `sembackend-db-1/gvrp_db`
  (or fall back to repository SQL DDL + migrations), and load fictional ST-* seed data.

.DESCRIPTION
  Zero credentials are stored in tracked files. Ephemeral test DB passwords and
  fictional test user passwords can be supplied via environment variables or
  auto-generated locally and written ONLY to gitignored
  `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json`.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/system-test/db/setup-test-db.ps1
#>
[CmdletBinding()]
param(
    [string]$ContainerName = "gvrp-system-test-db",
    [int]$HostPort = 3307,
    [string]$DatabaseName = "gvrp_test_db",
    [string]$AppUser = "gvrp_test_app",
    [string]$SourceContainer = "sembackend-db-1",
    [string]$SourceDatabase = "gvrp_db",
    [switch]$FallbackDdl,
    [switch]$RecreateContainer
)

# Use Continue so harmless Docker stderr warnings do not abort; check $LASTEXITCODE explicitly.
$ErrorActionPreference = "Continue"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$SeedFile = Join-Path $PSScriptRoot "seed-system-test.sql"
$TemplateEnvFile = Join-Path $RepoRoot "tools\system-test\postman\GVRP-Local-Test.postman_environment.template.json"
$LocalEnvFile = Join-Path $RepoRoot "tools\system-test\postman\GVRP-Local-Test.postman_environment.local.json"

function New-RandomToken([int]$Bytes = 16) {
    $buf = New-Object byte[] $Bytes
    $rng = [System.Security.Cryptography.RandomNumberGenerator]::Create()
    $rng.GetBytes($buf)
    $rng.Dispose()
    return ([System.BitConverter]::ToString($buf) -replace '-', '').ToLowerInvariant()
}

# 1. Verify Docker daemon is reachable
$null = docker info --format "{{.ServerVersion}}" 2>$null
if ($LASTEXITCODE -ne 0) {
    throw "Docker daemon is not running. Start Docker Desktop and re-run this script."
}

# 2. Ephemeral local-only credentials (from env or generated per run; never tracked)
$RootPass = if ($env:GVRP_TEST_DB_ROOT_PASSWORD) { $env:GVRP_TEST_DB_ROOT_PASSWORD } else { "st-root-" + (New-RandomToken 8) }
$AppPass = if ($env:GVRP_TEST_DB_APP_PASSWORD) { $env:GVRP_TEST_DB_APP_PASSWORD } else { "st-app-" + (New-RandomToken 8) }
$TestUserPass = if ($env:GVRP_TEST_USER_PASSWORD) { $env:GVRP_TEST_USER_PASSWORD } else { "StTest!2026#" + (New-RandomToken 4) }
$EngineInboundKey = if ($env:GVRP_TEST_ENGINE_INBOUND_KEY) { $env:GVRP_TEST_ENGINE_INBOUND_KEY } else { "st-eng-in-" + (New-RandomToken 12) }
$EngineCallbackKey = if ($env:GVRP_TEST_ENGINE_CALLBACK_KEY) { $env:GVRP_TEST_ENGINE_CALLBACK_KEY } else { "st-eng-cb-" + (New-RandomToken 12) }
$JwtSecret = if ($env:GVRP_TEST_JWT_SECRET) { $env:GVRP_TEST_JWT_SECRET } else { (New-RandomToken 32) }

# 3. Start or recreate isolated MySQL 8.0 container
$existing = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
if ($existing -and $RecreateContainer) {
    Write-Host "[db] Removing existing container $ContainerName..."
    docker rm -f $ContainerName 2>$null | Out-Null
    $existing = ""
}

if (-not $existing) {
    Write-Host "[db] Starting isolated MySQL 8.0 container '$ContainerName' on localhost:$HostPort..."
    docker run -d `
        --name $ContainerName `
        -p "${HostPort}:3306" `
        -e "MYSQL_ROOT_PASSWORD=$RootPass" `
        -e "MYSQL_DATABASE=$DatabaseName" `
        mysql:8.0 `
        --character-set-server=utf8mb4 `
        --collation-server=utf8mb4_unicode_ci 2>$null | Out-Null
    if ($LASTEXITCODE -ne 0) { throw "Failed to start container $ContainerName" }
} else {
    $running = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
    if (-not $running) {
        Write-Host "[db] Starting stopped container $ContainerName..."
        docker start $ContainerName 2>$null | Out-Null
    }
    Write-Host "[db] Reusing existing container $ContainerName (pass -RecreateContainer to rebuild)."
}

# 4. Wait for MySQL readiness (check TCP 127.0.0.1 + auth, ignoring temporary init socket)
Write-Host "[db] Waiting for $ContainerName to accept TCP connections..."
$ready = $false
for ($i = 0; $i -lt 45; $i++) {
    "SELECT 1;" | docker exec -i $ContainerName sh -c 'mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" --silent' 2>$null
    if ($LASTEXITCODE -eq 0) { $ready = $true; break }
    Start-Sleep -Seconds 2
}
if (-not $ready) { throw "MySQL container $ContainerName did not become ready in time." }
Write-Host "[db] $ContainerName is ready."

# 5. Recreate empty gvrp_test_db schema and dedicated least-privilege app user
$initSchemaSql = @"
DROP DATABASE IF EXISTS ``$DatabaseName``;
CREATE DATABASE ``$DatabaseName`` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE USER IF NOT EXISTS '$AppUser'@'%' IDENTIFIED BY '$AppPass';
ALTER USER '$AppUser'@'%' IDENTIFIED BY '$AppPass';
GRANT SELECT, INSERT, UPDATE, DELETE, CREATE, ALTER, DROP, INDEX, REFERENCES ON ``$DatabaseName``.* TO '$AppUser'@'%';
FLUSH PRIVILEGES;
"@
$initSchemaSql | docker exec -i $ContainerName sh -c 'mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" 2>/dev/null'
if ($LASTEXITCODE -ne 0) { throw "Failed to initialize database $DatabaseName and user $AppUser." }

# 6. Load schema DDL: clone --no-data from source container (default) OR fallback to repo DDL + migrations
$useClone = (-not $FallbackDdl)
if ($useClone) {
    $srcRunning = docker ps --filter "name=^/${SourceContainer}$" --format "{{.Names}}" 2>$null
    if (-not $srcRunning) {
        Write-Warning "[db] Source container '$SourceContainer' is not running; falling back to repository DDL + migrations."
        $useClone = $false
    }
}

if ($useClone) {
    Write-Host "[db] Cloning schema ONLY (--no-data) from $SourceContainer/$SourceDatabase into $ContainerName/$DatabaseName..."
    $schemaDump = docker exec $SourceContainer sh -c "mysqldump --no-data --routines --triggers --single-transaction -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $SourceDatabase 2>/dev/null"
    if ($LASTEXITCODE -ne 0 -or -not $schemaDump) {
        throw "Failed to dump schema from $SourceContainer/$SourceDatabase. Re-run with -FallbackDdl to load from repository SQL files."
    }
    $cleanDump = ($schemaDump -join "`n") -replace ' AUTO_INCREMENT=\d+', '' -replace '(?im)^\s*USE\s+`?gvrp_db`?\s*;', ''
    $cleanDump | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply cloned schema to $DatabaseName." }
    Write-Host "[db] Schema cloned cleanly (zero rows copied from $SourceDatabase)."
} else {
    Write-Host "[db] Loading schema from repository GVRP_master_initial.sql + 7 migrations..."
    $baseSql = Get-Content -Raw -Encoding UTF8 (Join-Path $RepoRoot "GVRP_Entry_API\src\main\resources\database\GVRP_master_initial.sql")
    $baseSql = $baseSql -replace '(?im)^\s*USE\s+`?gvrp_db`?\s*;', ''
    $baseSql | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
    if ($LASTEXITCODE -ne 0) { throw "Failed to apply GVRP_master_initial.sql." }

    $migrations = Get-ChildItem (Join-Path $RepoRoot "GVRP_Entry_API\src\main\resources\database\migrations\*.sql") | Sort-Object Name
    foreach ($m in $migrations) {
        Write-Host "[db] Applying migration $($m.Name)..."
        $mSql = (Get-Content -Raw -Encoding UTF8 $m.FullName) -replace '(?im)^\s*USE\s+`?gvrp_db`?\s*;', ''
        $mSql | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
        if ($LASTEXITCODE -ne 0) { throw "Failed to apply migration $($m.Name)." }
    }
    $seqSql = @"
CREATE TABLE IF NOT EXISTS route_sequence (next_val BIGINT) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS route_stop_sequence (next_val BIGINT) ENGINE=InnoDB;
CREATE TABLE IF NOT EXISTS unassigned_order_sequence (next_val BIGINT) ENGINE=InnoDB;
"@
    $seqSql | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
    if ($LASTEXITCODE -ne 0) { throw "Failed to create sequence tables." }
}

# 7. Generate BCrypt hash for fictional test users (in memory, never printed)
$env:GVRP_SEED_PW_TMP = $TestUserPass
$bcryptHash = py -c "import os, bcrypt; print(bcrypt.hashpw(os.environ['GVRP_SEED_PW_TMP'].encode('utf-8'), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode('ascii'))"
Remove-Item Env:GVRP_SEED_PW_TMP -ErrorAction SilentlyContinue
if ($LASTEXITCODE -ne 0 -or -not $bcryptHash.StartsWith('$2a$')) {
    throw "Failed to generate BCrypt hash via Python bcrypt."
}

# 8. Seed fictional ST-* data into gvrp_test_db
Write-Host "[db] Seeding fictional ST-* fixture into $DatabaseName..."
$seedSql = (Get-Content -Raw -Encoding UTF8 $SeedFile).Replace('__BCRYPT_PASSWORD_HASH__', $bcryptHash)
$seedSql | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
if ($LASTEXITCODE -ne 0) { throw "Failed to seed $DatabaseName." }

# 9. Emit gitignored local Postman environment file (without printing secrets)
if (Test-Path $TemplateEnvFile) {
    $envJson = Get-Content -Raw -Encoding UTF8 $TemplateEnvFile | ConvertFrom-Json
    $overrides = @{
        "testPassword"      = $TestUserPass
        "engineInboundKey"  = $EngineInboundKey
        "engineCallbackKey" = $EngineCallbackKey
        "dbHostPort"        = "$HostPort"
        "dbName"            = $DatabaseName
        "dbUser"            = $AppUser
        "dbPassword"        = $AppPass
        "jwtSecret"         = $JwtSecret
    }
    foreach ($v in $envJson.values) {
        if ($overrides.ContainsKey($v.key)) {
            $v.value = $overrides[$v.key]
        }
    }
    $envJson.name = "GVRP Local System Test (Generated Local - DO NOT COMMIT)"
    [System.IO.File]::WriteAllText($LocalEnvFile, ($envJson | ConvertTo-Json -Depth 10), (New-Object System.Text.UTF8Encoding($false)))
    Write-Host "[db] Wrote gitignored local Postman environment: $LocalEnvFile"
}

# 10. Verify table counts (non-sensitive counts only)
$countSql = "SELECT 'branches', COUNT(*) FROM $DatabaseName.branches UNION ALL SELECT 'users', COUNT(*) FROM $DatabaseName.users UNION ALL SELECT 'orders', COUNT(*) FROM $DatabaseName.orders UNION ALL SELECT 'solutions', COUNT(*) FROM $DatabaseName.solutions UNION ALL SELECT 'routes', COUNT(*) FROM $DatabaseName.routes UNION ALL SELECT 'route_sends', COUNT(*) FROM $DatabaseName.route_sends;"
$countSql | docker exec -i $ContainerName sh -c 'mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B 2>/dev/null'
Write-Host "[db] Done. Isolated test DB is ready on localhost:$HostPort/$DatabaseName."
