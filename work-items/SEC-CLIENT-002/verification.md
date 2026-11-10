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
