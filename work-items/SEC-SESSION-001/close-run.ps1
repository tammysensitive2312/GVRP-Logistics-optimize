# SEC-SESSION-001: record verify pass, user acceptance (2026-10-17 09:42), closure,
# then mark SEC-009 FIXED in the checklist (after the run is done, so the handoff hash stays valid).
# Usage: powershell -ExecutionPolicy Bypass -File "E:\OR-tools java example\GVRP-Logistics_Optimize\work-items\SEC-SESSION-001\close-run.ps1"
$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf  = 'tools/feature-workflow/workflow.mjs'
$wi  = 'work-items/SEC-SESSION-001'
$run = "$wi/run.json"

node $wf step $run pass "$wi/05-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'pass failed' }

$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
[ordered]@{
    decision = 'accepted'
    run_id   = 'SEC-SESSION-001'
    handoff  = $handoff
    source   = 'User message 2026-10-17 09:42 (+07:00) in the Claude session: manual disable/re-enable test passed ("toi test dung thanh cong").'
} | ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii

node $wf step $run accepted "$wi/06-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'accepted failed' }
node $wf step $run finish "$wi/07-proof.json"
if ($LASTEXITCODE -ne 0) { throw 'finish failed' }

$checklist = 'docs/security/SECURITY_CHECKLIST.md'
$text = [IO.File]::ReadAllText((Resolve-Path $checklist))
$old = 'IN PROGRESS: `work-items/SEC-SESSION-001` |'
$new = 'FIXED on branch `fix/sec-session-001` (`work-items/SEC-SESSION-001`, done 2026-10-17) |'
if (-not $text.Contains($old)) { throw 'checklist line not found' }
[IO.File]::WriteAllText((Resolve-Path $checklist), $text.Replace($old, $new))
Write-Host 'SEC-SESSION-001 done; SEC-009 marked FIXED.'
