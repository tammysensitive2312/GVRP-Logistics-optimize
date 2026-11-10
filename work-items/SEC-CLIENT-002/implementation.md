# Implementation — SEC-CLIENT-002

Date: 2026-11-10. Implementer: Claude Code (side session) in worktree `E:\gvrp-wt\sec-client-002`,
branch `fix/sec-client-002` from `master` `0b6b759`. Nothing committed. Paths below are relative to
`GVRP_Entry_client_v2/src/app/`.

## Scope

Audit lead #8 (`work-items/SEC-AUDIT-001/audit/NEEDS-VALIDATION.md` §8), confirmed in `context.md`:
late zone and route responses of branch A's session were drawn on branch B's map. Fix per
`design.md` D1, D2 (revised), D4.

## Baseline (before any code edit)

| Command | Exit | Result |
|---|---|---|
| `npm ci --prefer-offline` (worktree had no `node_modules`) | 0 | `package*.json` unchanged |
| `npx ng test --watch=false --browsers=ChromeHeadless` with only the new spec added | 1 | Executed 490 of 490, **5 FAILED**, 485 SUCCESS: all 484 existing specs pass (no pre-existing failures); MC-T1, T2, T3, T4, T6 fail, MC-T5 (control) passes |
| `rm -rf dist && npm run build` at `0b6b759` code | 0 | 12 warnings (10 component-style budgets, 1 initial bundle budget, 1 CommonJS `leaflet`); initial total 670.96 kB raw / 157.54 kB transfer |

## Changes

| File | Change |
|---|---|
| `shared/services/map.service.ts` | `private generation = 0`. Incremented in `initializeMap`, `clearRoutes` (so `displaySolution`, `clearSolution`, `clearAll`) and the new `reset()`. `drawZones()` captures it before subscribing and drops a stale `next`. `displaySolution` captures it after `clearRoutes`, passes it to `displayRoute(route, color, generation)`, stops the loop and skips `fitBounds` when stale. `displayRoute` checks after the stored-geometry await, after the OSRM await and in its `catch` before drawing. New public `reset()`: bumps the generation, `clearAll()` when a map is bound. The constructor registers `() => this.reset()` with `SessionResetService`. |
| `core/services/session-reset.service.ts` | New `registerResetHook(hook)`; `resetBranchState()` runs the registered hooks after the existing resets. No import of `MapService`. |

`drawZones()` and `drawStraightLine(...)` keep their signatures (used by bracket access in
`map.service.xss.spec.ts`).

## Deviation from design

D2 first called `this.injector.get(MapService).reset()` from `SessionResetService`. Built that way,
the initial bundle grew from 670.96 kB to 847.35 kB raw (MapService and Leaflet moved into it; same
12 warnings). Replaced by the reset-hook registration (design.md D2 "Revised"); initial total after:
670.87 kB.

## New tests (new spec file only)

`shared/services/map.service.session.spec.ts` — 6 specs (MC-T1..T6 of `design.md` D4), real
Leaflet maps, real `SessionResetService`, Subjects for `LezApiService.listZones/routeGeometry` and
`RoutingService.getRouteGeometry`.

## Commands with exit codes (after)

| Command | Exit | Result |
|---|---|---|
| `npx ng test --watch=false --browsers=ChromeHeadless` | 0 | Executed 490 of 490 SUCCESS (484 baseline + 6 new) |
| same, with the `registerResetHook` line in `MapService` removed (mutation; file restored and byte-compared) | 1 | 1 FAILED: MC-T4 — the session-reset wiring is covered by a spec |
| `rm -rf dist && npm run build` | 0 | 12 warnings, same set as baseline; initial total 670.87 kB raw / 157.29 kB transfer |

The `'Polling error:', Error: network` console lines appear 12 times before and after (pre-existing,
not failures).

## Modified Tests

None. No existing spec, fixture or helper was edited.

## Security-Relevant Changes

- Closes the client-side path of audit lead #8: zone polygons/names and route polylines, tooltips
  (plate, order count) and stop markers of the previous principal are no longer drawn after a
  session reset or on a new map; a reset also removes what was drawn from a still-open map.
- No new endpoint, `permitAll`, dependency or logging. No server change.

## Remaining Uncertainty

- NOT_VERIFIED in a real browser: the timing (a response still in flight across logout, login of
  another planner and creation of the new `<app-map>`). Left to the parent's manual check.
- The HTTP requests themselves still complete after logout; only their effect is dropped.
- `SECURITY_CHECKLIST.md` not edited here (status update belongs to the handoff after the browser
  check).

## Lesson Candidates

- Importing a lazily used root service (here `MapService`, which imports Leaflet) into a service of
  the initial bundle (`SessionResetService`) silently moves the library into the initial bundle; the
  warning count stays the same, only the "Initial total" line shows it. Compare that line, not only
  the warning set.
