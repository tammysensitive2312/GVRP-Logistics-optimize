# SEC-ROLE-001: record verify pass, the user's acceptance (2026-10-16 23:48) and closure.
# Usage: powershell -ExecutionPolicy Bypass -File "E:\OR-tools java example\GVRP-Logistics_Optimize\work-items\SEC-ROLE-001\close-run.ps1"
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf  = 'tools/feature-workflow/workflow.mjs'
$wi  = 'work-items/SEC-ROLE-001'
$run = "$wi/run.json"

node $wf step $run pass "$wi/05-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'pass failed' }

$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
$acceptance = [ordered]@{
    decision = 'accepted'
    run_id   = 'SEC-ROLE-001'
    handoff  = $handoff
    source   = 'User message 2026-10-16 23:48 (+07:00) in the Claude session: clean test 80/80, DRIVER 403 body 0040301 on /orders, "tat ca deu oke".'
}
$acceptance | ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii

node $wf step $run accepted "$wi/06-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'accepted failed' }
node $wf step $run finish "$wi/07-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'finish failed' }
