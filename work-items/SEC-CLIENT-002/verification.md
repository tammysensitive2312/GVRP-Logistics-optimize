# Verification — SEC-CLIENT-002 (automated checks only)

Verifier: Claude Code (side session), 2026-11-10. Version: worktree `E:\gvrp-wt\sec-client-002`,
branch `fix/sec-client-002` at `0b6b759` + uncommitted changes listed in `implementation.md`.
The manual two-planner browser check and the `pass` transition are left to the parent session.

| Check | Command (in `GVRP_Entry_client_v2`) | Outcome |
|---|---|---|
| New specs fail on unfixed code | `npx ng test --watch=false --browsers=ChromeHeadless` at `0b6b759` + new spec | exit 1; 490 executed, 5 FAILED (MC-T1, T2, T3, T4, T6), 485 SUCCESS |
| Client specs after fix | same | exit 0; **490 of 490 SUCCESS** (484 baseline + 6 new) |
| Reset wiring covered | same, with `registerResetHook` registration removed (restored, byte-compared) | exit 1; MC-T4 FAILED only |
| Client build | `rm -rf dist && npm run build` | exit 0; 12 warnings, same set as baseline; initial total 670.87 kB (baseline 670.96 kB) |

## Requirement coverage

| Requirement | Evidence | Status |
|---|---|---|
| MC-R1 | `context.md` failing-spec table | VERIFIED |
| MC-R2 | MC-T1, MC-T4 | VERIFIED (unit); browser NOT_VERIFIED |
| MC-R3 | MC-T2, MC-T3, MC-T6 | VERIFIED (unit); browser NOT_VERIFIED |
| MC-R4 | static inspection of depots, orders, zone-map, driver-view, pickers (`context.md`) | PARTIALLY_VERIFIED |
| MC-R5 | MC-T5 and the unchanged existing map specs | VERIFIED (unit); browser NOT_VERIFIED |

## Modified tests

None. One new spec file: `GVRP_Entry_client_v2/src/app/shared/services/map.service.session.spec.ts`.

## Run ledger note

`design.md` and `traceability.md` were revised after the design transition (D2 bundle deviation,
statuses updated); `recover` reports them as stale historical evidence. The revision is recorded in
a checkpoint at state `verify`.

## Remaining for the parent

Manual browser check (see the side-session report), then `pass` with `verification` + `handoff`.

## Runtime (parent, 2026-11-11)

Runtime environment (Claude, 2026-11-11 +07:00): integration worktree `E:\gvrp-wt\sec-batch2` (branch `test/sec-batch2` = master `0b6b759` + `fix/sec-auth-002` + `fix/sec-low-001` + `fix/sec-client-002`), Engine + Entry + client dev server run from it with the git-ignored local property files; MySQL in Docker. User signed in as planner of branch 1000 ("Branch Huge 1"); authenticated calls were made from that page with its session (token never printed). Merged suites: Entry 369/369 (`SPRING_PROFILES_ACTIVE=ci`, CI exclusions), client 490/490 specs, client build exit 0 with the same 12 warnings (initial 670.87 kB).

| Check | Result |
|---|---|
| Branch 1000 planner opens job 91's solution on the dashboard | routes drawn (12 coloured routes, 127 markers), `GET /api/v1/zones` 200 |
| Console during the session | only the errors caused by the deliberate 400/409/405 API probes of the other items; none from the map |
| Two planners of different branches with throttled `/zones` and `/routes/*/geometry` (verification.md §manual check) | NOT RUN: needs a second account and network throttling. Covered by `map.service.session.spec.ts` MC-T1…T6 (5 failed before the fix) |
