# SEC-TENANT-001: record workflow transitions up to `verify`, then run the
# Entry API test suite. Output goes to verify-output.log and mvn-test.log in this
# folder (both gitignored by *.log). The agent reads them afterwards.
#
# Usage:
#   powershell -ExecutionPolicy Bypass -File "E:\OR-tools java example\GVRP-Logistics_Optimize\work-items\SEC-TENANT-001\run-verify.ps1"
#
# Safe to re-run: transitions already recorded are skipped. The script never
# commits, pushes or modifies source files.

$ErrorActionPreference = 'Continue'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
Set-Location $root

$wi     = 'work-items/SEC-TENANT-001'
$run    = "$wi/run.json"
$wf     = 'tools/feature-workflow/workflow.mjs'
$log    = Join-Path $root "$wi/verify-output.log"
$mvnLog = Join-Path $root "$wi/mvn-test.log"

function Log([string]$text) {
    Write-Host $text
    Add-Content -Path $log -Value $text -Encoding UTF8
}

Set-Content -Path $log -Value "=== run-verify.ps1 $(Get-Date -Format o)" -Encoding UTF8

# ---------- git ----------
Log '--- git (before)'
$branch = (git rev-parse --abbrev-ref HEAD).Trim()
Log "branch: $branch"
Log "head:   $((git rev-parse HEAD).Trim())"
Log ((git status --short) -join "`n")
if ($branch -eq 'master' -or $branch -eq 'main') {
    git switch -c fix/sec-tenant-001 2>&1 | ForEach-Object { Log "$_" }
    Log "switched to: $((git rev-parse --abbrev-ref HEAD).Trim())"
}

# ---------- workflow ledger ----------
Log '--- workflow'
if (-not (Test-Path $run)) {
    $out = node $wf init $run . SEC-TENANT-001 GVRP_Entry_API/src docs/security 2>&1
    Log "init: $out (exit $LASTEXITCODE)"
    if ($LASTEXITCODE -ne 0) { Log 'STOP: init failed'; exit 1 }
}

$proofByState = @{
    'intake'    = "$wi/01-proof.json"
    'discover'  = "$wi/02-proof.json"
    'design'    = "$wi/03-proof.json"
    'implement' = "$wi/04-proof.json"
}
while ($true) {
    $state = (Get-Content $run -Raw | ConvertFrom-Json).state
    if (-not $proofByState.ContainsKey($state)) { break }
    $out = node $wf step $run ready $proofByState[$state] 2>&1
    Log "step from ${state}: $out (exit $LASTEXITCODE)"
    if ($LASTEXITCODE -ne 0) { Log "STOP: transition from $state failed"; exit 1 }
}
$status = Get-Content $run -Raw | ConvertFrom-Json
Log "run state: $($status.state), revision: $($status.revision)"

# ---------- tests ----------
Log '--- mvnw.cmd test (GVRP_Entry_API, full suite)'
Log 'Note: @SpringBootTest classes need the local MySQL Docker container running.'
Push-Location GVRP_Entry_API
& .\mvnw.cmd -B test *>&1 | Out-File -FilePath $mvnLog -Encoding UTF8
$testExit = $LASTEXITCODE
Pop-Location
Log "mvn exit code: $testExit"

Log '--- surefire summary'
Get-ChildItem 'GVRP_Entry_API/target/surefire-reports' -Filter '*.txt' -ErrorAction SilentlyContinue |
    Sort-Object Name |
    ForEach-Object { Log ((Get-Content $_.FullName | Select-Object -First 4) -join ' | ') }

Log '--- compile errors / failures (from mvn-test.log)'
Select-String -Path $mvnLog -Pattern 'COMPILATION ERROR|\[ERROR\]|Tests run:.*Fail|BUILD' |
    Select-Object -First 80 |
    ForEach-Object { Log $_.Line }

Log '=== done. Tell the agent the script finished.'
