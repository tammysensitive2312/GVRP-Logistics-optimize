$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf = 'tools/feature-workflow/workflow.mjs'; $wi = 'work-items/PARETO-002'; $run = "$wi/run.json"
$expected = 'f1bb06021795dcd01caa86b6a96346852412e67680e8c0c193a7d53f89425dc1'
function Step($event, $proof) {
  node $wf step $run $event "$wi/$proof"; if ($LASTEXITCODE -ne 0) { throw "$event failed" }
}
Step 'changed' '06-proof.json'
Step 'ready'   '07-proof.json'
Step 'pass'    '08-proof.json'
$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
if ($handoff -ne $expected) {
  throw "Scope changed since verification ($handoff != $expected). Run left at await_user_test; re-verify first."
}
[ordered]@{ decision='accepted'; run_id='PARETO-002'; handoff=$handoff;
  source='User message 2026-10-19 00:35 (+07:00) in the Claude session: "toi xac nhan test tren man hinh da chap nhan duoc" (screen test of the combined PARETO-002 + PARETO-003 tree)' } |
  ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii
Step 'accepted' '09-proof.json'
Step 'finish'   '10-proof.json'
Write-Host "PARETO-002 closed: $((Get-Content $run -Raw | ConvertFrom-Json).state)"
