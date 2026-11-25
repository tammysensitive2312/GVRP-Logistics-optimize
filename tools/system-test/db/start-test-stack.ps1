<#
.SYNOPSIS
  Launch GVRP_Engine_API (:8081) and/or GVRP_Entry_API (:8080) against the isolated
  `gvrp_test_db` on port 3307 using the gitignored local Postman environment file.

.DESCRIPTION
  Reads ephemeral connection and key values from
  `tools/system-test/postman/GVRP-Local-Test.postman_environment.local.json`.
  All configuration overrides and secrets are passed strictly via process
  environment variables (Spring Boot relaxed binding), avoiding cmd.exe `&`
  escaping issues and never touching tracked files or `application-local.properties`.
#>
[CmdletBinding()]
param(
    [switch]$StartEngine,
    [switch]$StartEntry,
    [switch]$Foreground,
    [string]$JdkHome = "C:\Program Files\Java\jdk-21",
    [string]$LocalEnvPath = ""
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path
if (-not $LocalEnvPath) {
    $LocalEnvPath = Join-Path $RepoRoot "tools\system-test\postman\GVRP-Local-Test.postman_environment.local.json"
}

if (-not (Test-Path $LocalEnvPath)) {
    throw "Missing $LocalEnvPath. Run tools/system-test/db/setup-test-db.ps1 first."
}
if (-not (Test-Path $JdkHome)) {
    throw "JDK 21 directory not found at $JdkHome."
}

$rawEnv = (Get-Content -Raw -Encoding UTF8 $LocalEnvPath) | ConvertFrom-Json
$vars = @{}
foreach ($item in $rawEnv.values) {
    $vars[$item.key] = [string]$item.value
}

$reportsDir = Join-Path $RepoRoot "tools\system-test\reports"
New-Item -ItemType Directory -Force -Path $reportsDir | Out-Null

# Shared environment overrides inherited by child Maven/Spring processes
$env:JAVA_HOME = $JdkHome
$env:Path = "$JdkHome\bin;$env:Path"
$env:SPRING_PROFILES_ACTIVE = "systemtest"
$env:GVRP_DB_APP_PASSWORD = $vars["dbPassword"]
$env:GVRP_JWT_SECRET = $vars["jwtSecret"]
$env:GVRP_ENGINE_CALLBACK_KEY = $vars["engineCallbackKey"]
$env:GVRP_ENGINE_INBOUND_KEY = $vars["engineInboundKey"]
$env:GVRP_MAIL_USERNAME = "test"
$env:GVRP_MAIL_PASSWORD = "test"
$env:GVRP_GOONG_API_KEY = "test"

if (-not $StartEngine -and -not $StartEntry) {
    $StartEngine = $true
    $StartEntry = $true
}

if ($Foreground -and $StartEngine -and $StartEntry) {
    throw "-Foreground supports launching one service at a time (-StartEngine or -StartEntry)."
}

if ($StartEngine) {
    $engineDir = Join-Path $RepoRoot "GVRP_Engine_API"
    $env:SERVER_PORT = "8081"
    $env:SPRING_OPTIMIZATION_ENTRY_URL = "http://localhost:8080/api/v1"
    if ($Foreground) {
        Write-Host "[stack] Starting GVRP_Engine_API on :8081 (foreground)..."
        Set-Location $engineDir
        & (Join-Path $engineDir "mvnw.cmd") spring-boot:run
        exit $LASTEXITCODE
    } else {
        Write-Host "[stack] Launching GVRP_Engine_API on :8081..."
        $engineLog = Join-Path $reportsDir "engine-api.log"
        $engineErr = Join-Path $reportsDir "engine-api.err.log"
        Start-Process -FilePath (Join-Path $engineDir "mvnw.cmd") `
            -ArgumentList @("spring-boot:run") `
            -WorkingDirectory $engineDir `
            -RedirectStandardOutput $engineLog `
            -RedirectStandardError $engineErr
    }
}

if ($StartEntry) {
    $entryDir = Join-Path $RepoRoot "GVRP_Entry_API"
    $env:SERVER_PORT = "8080"
    $env:SPRING_DATASOURCE_URL = "jdbc:mysql://localhost:$($vars['dbHostPort'])/$($vars['dbName'])?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=Asia/Ho_Chi_Minh"
    $env:SPRING_DATASOURCE_USERNAME = $vars["dbUser"]
    $env:SPRING_OPTIMIZATION_ENGINE_URL = "http://localhost:8081/api/v1"
    $env:GEOCODING_ENABLED = "false"
    $env:SPRING_MAIL_HOST = "localhost"
    $env:SPRING_MAIL_PORT = "2525"
    if ($Foreground) {
        Write-Host "[stack] Starting GVRP_Entry_API on :8080 pointing to localhost:$($vars['dbHostPort'])/$($vars['dbName']) (foreground)..."
        Set-Location $entryDir
        & (Join-Path $entryDir "mvnw.cmd") spring-boot:run
        exit $LASTEXITCODE
    } else {
        Write-Host "[stack] Launching GVRP_Entry_API on :8080 pointing to localhost:$($vars['dbHostPort'])/$($vars['dbName'])..."
        $entryLog = Join-Path $reportsDir "entry-api.log"
        $entryErr = Join-Path $reportsDir "entry-api.err.log"
        Start-Process -FilePath (Join-Path $entryDir "mvnw.cmd") `
            -ArgumentList @("spring-boot:run") `
            -WorkingDirectory $entryDir `
            -RedirectStandardOutput $entryLog `
            -RedirectStandardError $entryErr
    }
}
