<#
.SYNOPSIS
  Fast-reset `gvrp_test_db` on port 3307 back to the clean ST-* baseline seed
  without recreating the database or rotating local credentials/keys.

.DESCRIPTION
  Reads `testPassword` and `dbName` from gitignored
  `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json`,
  computes the BCrypt ($2a$10$) hash in memory, and executes `seed-system-test.sql`
  inside `gvrp-system-test-db`. Safe to run while Entry API (:8080) and
  Engine API (:8081) remain running.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File tools/system-test/db/reseed-test-db.ps1
#>
[CmdletBinding()]
param(
    [string]$ContainerName = "gvrp-system-test-db",
    [string]$LocalEnvPath = ""
)

$ErrorActionPreference = "Continue"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
$SeedFile = Join-Path $PSScriptRoot "seed-system-test.sql"

if (-not $LocalEnvPath) {
    $LocalEnvPath = Join-Path $RepoRoot "tools\system-test\postman\GVRP-Local-Test.postman_environment.local.json"
}

if (-not (Test-Path $LocalEnvPath)) {
    throw "Missing $LocalEnvPath. Run tools/system-test/db/setup-test-db.ps1 first."
}

$rawEnv = (Get-Content -Raw -Encoding UTF8 $LocalEnvPath) | ConvertFrom-Json
$vars = @{}
foreach ($item in $rawEnv.values) {
    $vars[$item.key] = [string]$item.value
}

$DatabaseName = if ($vars["dbName"]) { $vars["dbName"] } else { "gvrp_test_db" }
$TestUserPass = if ($env:GVRP_TEST_USER_PASSWORD) { $env:GVRP_TEST_USER_PASSWORD } else { $vars["testPassword"] }

if (-not $TestUserPass) {
    throw "Missing testPassword in $LocalEnvPath and GVRP_TEST_USER_PASSWORD."
}

$running = docker ps --filter "name=^/${ContainerName}$" --format "{{.Names}}" 2>$null
if (-not $running) {
    throw "Container '$ContainerName' is not running. Run setup-test-db.ps1 first."
}

# Generate BCrypt hash in memory (never printed)
$env:GVRP_SEED_PW_TMP = $TestUserPass
$bcryptHash = py -c "import os, bcrypt; print(bcrypt.hashpw(os.environ['GVRP_SEED_PW_TMP'].encode('utf-8'), bcrypt.gensalt(rounds=10, prefix=b'2a')).decode('ascii'))"
Remove-Item Env:GVRP_SEED_PW_TMP -ErrorAction SilentlyContinue
if ($LASTEXITCODE -ne 0 -or -not $bcryptHash.StartsWith('$2a$')) {
    throw "Failed to generate BCrypt hash via Python bcrypt."
}

$seedSql = (Get-Content -Raw -Encoding UTF8 $SeedFile).Replace('__BCRYPT_PASSWORD_HASH__', $bcryptHash)
$seedSql | docker exec -i $ContainerName sh -c "mysql -uroot -p`"`$MYSQL_ROOT_PASSWORD`" $DatabaseName 2>/dev/null"
if ($LASTEXITCODE -ne 0) {
    throw "Failed to reseed $DatabaseName inside $ContainerName."
}

$countSql = "SELECT 'branches', COUNT(*) FROM $DatabaseName.branches UNION ALL SELECT 'users', COUNT(*) FROM $DatabaseName.users UNION ALL SELECT 'orders', COUNT(*) FROM $DatabaseName.orders UNION ALL SELECT 'solutions', COUNT(*) FROM $DatabaseName.solutions UNION ALL SELECT 'routes', COUNT(*) FROM $DatabaseName.routes UNION ALL SELECT 'route_sends', COUNT(*) FROM $DatabaseName.route_sends;"
$countSql | docker exec -i $ContainerName sh -c 'mysql -h 127.0.0.1 -uroot -p"$MYSQL_ROOT_PASSWORD" -N -B 2>/dev/null'
Write-Host "[db] Reseeded $DatabaseName cleanly."
