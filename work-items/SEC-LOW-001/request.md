# Request — SEC-LOW-001 (vehicle plate oracle, import bounds, retention sweep)

Source: Claude Code session 2026-11-10 (+07:00). The parent Claude session delegated the open
low-severity security group to a sub-agent, worktree `E:\gvrp-wt\sec-low-001`, branch
`fix/sec-low-001`, base `master` @ `0b6b759`. Each lead must first be confirmed or refuted from source
and with a new failing test before any fix.

## Findings addressed

| Finding | Severity | Evidence |
|---|---|---|
| SEC-007 / audit lead #11 `GVRP-vehicle-global-license-plate-check` | Low | `VehicleService.createVehicle` checks the plate globally before any branch-scoped check; runtime-confirmed 2026-11-04: existing foreign plate → 500, new plate → 400 (`work-items/SEC-AUDIT-001/runtime-validation.md`). Update path unchecked. |
| Audit lead #10 `OrderImportService:import-row-amplification` | — (needs_validation) | No row-count bound on order import; one synchronous paid Goong geocoding call per address-only row (`work-items/SEC-AUDIT-001/audit/NEEDS-VALIDATION.md` §10). |
| Audit lead #14 `JobCleanupService:incomplete-revision-descendant-delete-chain` | — (needs_validation) | Retention sweep deletes only direct revisions; a nested revision chain (ON DELETE RESTRICT) aborts the global sweep (§14). |

## Requirements

| ID | Requirement |
|---|---|
| LOW-R1 | Vehicle create never answers 500 for a duplicate plate. A duplicate plate is refused with 409 and a message that does not say which branch holds the plate. |
| LOW-R2 | Vehicle create validates the caller's own fleet, depots and vehicle type before the plate is checked, so a caller without valid own references gets the same answer whether or not the plate exists elsewhere. |
| LOW-R3 | Vehicle update that changes the plate to one already in use is refused with 409 (not a database error / 500); keeping the own plate is accepted. A unique-constraint race on save also answers 409. |
| LOW-R4 | An order import above a configurable row ceiling is refused with 400 before any geocoding call or database write. |
| LOW-R5 | An order import whose address-only rows exceed a configurable geocoding ceiling is refused with 400 before any geocoding call. |
| LOW-R6 | The retention sweep deletes the whole revision tree of an expired job (sends first, then revisions deepest first, then the root, then the job). |
| LOW-R7 | One job that cannot be deleted does not stop the sweep: its changes roll back, it is logged by id, and later jobs of every status are still processed. |
| LOW-R8 | Existing accepted behaviour stays: existing tests green, imports below the ceilings unchanged, dry-run default unchanged. |

## Exclusions

- Per-branch plate uniqueness: needs a schema change; designed in `design.md` as a pending user decision, not applied (agents never run DDL on `gvrp_db`).
- Geocoding cache, rate limiting per branch, Goong quota: separate decisions.
- Multipart size limits (`spring.servlet.multipart.*`): framework defaults stay.
- Runtime verification on the live stack and the handoff: done by the parent session.
