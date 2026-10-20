$ErrorActionPreference = 'Stop'
Set-Location (Resolve-Path (Join-Path $PSScriptRoot '..\..')).Path
$wf = 'tools/feature-workflow/workflow.mjs'; $wi = 'work-items/CI-CLIENT-001'; $run = "$wi/run.json"
$handoff = (Get-Content $run -Raw | ConvertFrom-Json).handoff
[ordered]@{ decision='accepted'; run_id='CI-CLIENT-001'; handoff=$handoff;
  source='User message 2026-10-20 07:31 (+07:00) in the Claude session: "toi test oke ci-client roi"' } |
  ConvertTo-Json | Set-Content "$wi/user-acceptance.json" -Encoding ascii
node $wf step $run accepted "$wi/06-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'accepted failed' }
node $wf step $run finish "$wi/07-proof.json"; if ($LASTEXITCODE -ne 0) { throw 'finish failed' }
Write-Host "CI-CLIENT-001 closed: $((Get-Content $run -Raw | ConvertFrom-Json).state)"
