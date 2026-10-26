# Feature traceability

Feature ID: LEZ-001
Inspected source revision: `master @ 617b720` (2026-10-25)
Request source: `work-items/LEZ-001/request.md` (user messages 2026-10-24 16:12 → 2026-10-25 23:51 +07:00)

Code and test links are **Planned**. Nothing is implemented (implementation is not authorized).

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| LEZ-R01 | System zone templates, read-only for branches (request §1, 12:08) | §2.1, §6 | Planned: `entity/ZoneTemplate`, `ZoneRuleTemplate`, migration + seed | AC-07, AC-08 | None | NOT_VERIFIED |
| LEZ-R02 | LINKED / COPIED branch zones; copies never overwritten, notice on update (21:20) | §2.2, §6 | Planned: `entity/BranchZone`, `BranchZoneRule`, `ZoneService` | AC-07 | None | NOT_VERIFIED |
| LEZ-R03 | Selector + schedule + effective range rules (23:51) | §3.1–3.3 | Planned: `RuleEvaluator`, `ScheduleNormalizer` | AC-01, AC-11, AC-12 | None | NOT_VERIFIED |
| LEZ-R04 | Legal attributes separate from capacity; missing = non-compliant (21:20, 23:51) | §2.3, §3.1, §3.4 | Planned: `VehicleType` columns, dialog | AC-04 | None | NOT_VERIFIED |
| LEZ-R05 | Vehicle permits (design accepted 21:20) | §2.2, §3.3 step 4 | Planned: `entity/VehiclePermit`, permits API | AC-03 | None | NOT_VERIFIED |
| LEZ-R06 | Hard all-day enforcement at optimisation; unassigned reason (12:08) | §5.1 | Planned: `OptimizationService` skill injection, post-solve assert | AC-01, AC-02 | None | NOT_VERIFIED |
| LEZ-R07 | Optimiser avoids time-window conflicts | §5.5 | Planned, **P2 run** | — | None | NOT_VERIFIED |
| LEZ-R08 | Transit detection on the server path | §5.2, §5.3 | Planned: engine leg geometry, `ComplianceChecker` | AC-05 | None | NOT_VERIFIED |
| LEZ-R09 | Depot in zone = presence; config warning (23:51) | §4, §5.1 | Planned: `RestrictionCompiler.unusableVehicles` | AC-06 | None | NOT_VERIFIED |
| LEZ-R10 | Evaluate reports ZONE_ACCESS / ZONE_TRANSIT | §5.3 trigger (b), §7 | Planned: `RouteCustomizationService` + checker | AC-05 | None | NOT_VERIFIED |
| LEZ-R11 | Send guard (21:20 decision context) | §5.4 | Planned: `RouteSendService.send` | AC-03, AC-05, AC-12 | None | NOT_VERIFIED |
| LEZ-R12 | Driver map uses server geometry + instructions, P1 (23:24, 23:51) | §5.2, §8 | Planned: `RouteStop.geometry_to_next`, driver DTO, `driver-view.component` | AC-09 | None | NOT_VERIFIED |
| LEZ-R13 | Rule snapshot per job | §4 | Planned: `OptimizationJob.lez_snapshot` | AC-10 | None | NOT_VERIFIED |
| LEZ-R14 | Tenant isolation | §10 | Planned: repositories `findByIdAndBranchId` | AC-08 | None | NOT_VERIFIED |
| LEZ-R15 | Badge + compliance panel | §7, §8 | Planned: client orders table, compliance panel | AC-02 (UI) | None | NOT_VERIFIED |
| LEZ-R16 | Hanoi seed phase 2–3, not phase 1 (23:51) | §3.5 | Planned: seed migration | AC-11 | None | NOT_VERIFIED |
| LEZ-R17 | Avoidance routing | §13 | **P3 run** | — | None | NOT_VERIFIED |
| LEZ-R18 | Reporting + experiment | — | **P4 run**; experiment under `docs/research` protocol | — | None | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Shared templates; branch enables or copies | 2026-10-25 12:08 | R01, R02 | — |
| Hard mode, with permits as the exception mechanism | 12:08; 21:20 ("cần phải xem luật"); 23:51 agreed after the regulation research | R05, R06, R11 | — |
| Legal attributes separate from capacity; `legalClass` explicit; missing = non-compliant | 21:20; 23:51 | R04 | — |
| Stricter (gross) reading of "2 tấn" until the original text is checked | 23:51 | R16 | Verify the Đề án text |
| Scope = internal fleets | 23:24 | R03 (`appBased`) | — |
| Driver map on server geometry and instructions in P1 | 23:24; 23:51 | R12 | — |
| Do not seed Hanoi phase 1 | 23:51 | R16 | — |
| **Design-level:** checker evaluates PARTIAL in P1; only optimiser avoidance waits for P2 | Agent proposal, consistent with hard mode | R07, R08, R11 | User review of design §5.3 |
| Open: D1 planner map, D2 template authoring, D3 run split | — | R12, R01, R07/R17/R18 | Ask at design review |

## Synchronization review

- Requirements versus implementation: not applicable (no implementation).
- Design versus implementation: not applicable.
- Test and documentation coverage: AC-01…AC-12 cover R01–R16 except the UI-only parts of R15, which are covered by browser verification later.
- Stale links or changed assumptions: none at revision `617b720`.
- Remaining limitations: see design §13.
