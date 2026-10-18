$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf = 'tools/feature-workflow/workflow.mjs'; $wi = 'work-items/PARETO-001'; $run = "$wi/run.json"
node $wf step $run pass "$wi/05-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'pass failed' }
$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
[ordered]@{ decision='accepted'; run_id='PARETO-001'; handoff=$handoff;
  source='User message 2026-10-18 18:03 (+07:00) in the Claude session: "duoc roi ban, toi xac nhan test API thanh cong" (manual API test passed; also confirms review F1)' } |
  ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii
node $wf step $run accepted "$wi/06-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'accepted failed' }
node $wf step $run finish "$wi/07-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'finish failed' }
Write-Host "PARETO-001 closed: $((Get-Content $run -Raw | ConvertFrom-Json).state)"
