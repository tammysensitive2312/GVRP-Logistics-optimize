<#
.SYNOPSIS
  Stop and remove the isolated system-test MySQL container and optional local runtime env.
#>
[CmdletBinding()]
param(
    [string]$ContainerName = "gvrp-system-test-db",
    [switch]$RemoveLocalPostmanEnv
)

$ErrorActionPreference = "Stop"
$RepoRoot = (Resolve-Path (Join-Path $PSScriptRoot "..\..\..")).Path

$existing = docker ps -a --filter "name=^/${ContainerName}$" --format "{{.Names}}"
if ($existing) {
    docker rm -f $ContainerName | Out-Null
    Write-Host "[db] Removed container $ContainerName."
} else {
    Write-Host "[db] Container $ContainerName does not exist."
}

if ($RemoveLocalPostmanEnv) {
    $localEnv = Join-Path $RepoRoot "tools\system-test\postman\GVRP-Local-Test.postman_environment.local.json"
    if (Test-Path $localEnv) {
        Remove-Item -Force $localEnv
        Write-Host "[db] Removed $localEnv."
    }
}
