# Handoff — SEC-ENGINE-001

Version: branch `fix/sec-engine-001` in `E:\gvrp-wt\sec-engine-001`, uncommitted on top of `bdbaf29`.
Closes when accepted: SEC-010 and audit leads #6 and #7 (`EngineApiController.no-auth-boundary`,
`EngineOptimization.unbounded-configured-work`).

## What changed for you

- The Engine now refuses every request without `X-Engine-Key`, except `GET /api/v1/optimization/health`.
- Both services read the key from `GVRP_ENGINE_INBOUND_KEY` or from the git-ignored
  `application-local.properties` (`engine.inbound-key` in Entry, `gvrp.security.inbound-key` in Engine).
  A key was generated for your local files on 2026-11-10; the value was never printed.
- Anyone running the Engine elsewhere (another machine, CI, a benchmark script that calls the Engine
  directly) must set the same key on both sides, or the Engine answers 401.
- Engine requests above the ceilings are refused (400/413); every solve stops after 7200 s at most.
  Entry's presets and all recorded runs stay below these limits.

## User test steps

1. Run Entry and Engine as usual (local property files already contain the key).
2. In the app, plan a route for a few unassigned orders with suitable vehicles.
   Expected: the job completes as before.
3. Optional: `curl -i -X POST http://localhost:8081/api/v1/optimization/evaluate -H "Content-Type: application/json" -d "{}"`.
   Expected: `401` with `{"status":"UNAUTHORIZED"}`. `curl -i http://localhost:8081/api/v1/optimization/health` → 200.

Claude ran these steps on 2026-11-10 (see `verification.md`, job 96).

## Known limitations

See `verification.md` § Limitations: async concurrency limit not verified, matrix build not under the
runtime cap, no pre-parse body-size limit, port exposure unchanged.
