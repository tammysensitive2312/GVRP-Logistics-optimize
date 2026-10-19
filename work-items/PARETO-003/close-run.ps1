$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf = 'tools/feature-workflow/workflow.mjs'; $wi = 'work-items/PARETO-003'; $run = "$wi/run.json"
$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
[ordered]@{ decision='accepted'; run_id='PARETO-003'; handoff=$handoff;
  source='User message 2026-10-19 00:35 (+07:00) in the Claude session: "toi xac nhan test tren man hinh da chap nhan duoc"' } |
  ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii
node $wf step $run accepted "$wi/06-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'accepted failed' }
node $wf step $run finish "$wi/07-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'finish failed' }
Write-Host "PARETO-003 closed: $((Get-Content $run -Raw | ConvertFrom-Json).state)"
