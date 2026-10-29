# Task for the OpenCode engineer — BROWSER-FIX-001

You are `gvrp-engineer`. Implementation is authorized by the user (2026-10-29 07:23 +07:00, "lập task để fix các bug bạn nêu ra đi"; choices at 07:25 recorded in `request.md`). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root, on Windows (L-2026-014).

## Read first
1. `work-items/BROWSER-FIX-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `work-items/MAP-ORDERS-001/design.md` §D2 (`localDateIso`) and its `implementation.md`
5. `docs/engineering/LESSONS.md`: L-2026-001, -003, -007, -009, -010, -011, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
This task is **stacked on MAP-ORDERS-001** (it edits `orders-section.component.ts` and uses `shared/utils/date.utils.ts`).
1. `work-items/MAP-ORDERS-001/implementation.md` must exist and `node tools/feature-workflow/workflow.mjs status work-items/MAP-ORDERS-001/run.json` must show state `verify`. Otherwise stop and report.
2. The user commits `fix/map-orders-001` before you start. On that branch, `git log -1 --oneline` and `git status --short`: the only uncommitted paths allowed are `work-items/BROWSER-FIX-001/`, `work-items/LEZ-001/review-p1c.md`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report the list.
3. `node tools/session-brief.mjs` — include the output in your report.
4. `git switch -c fix/browser-fix-001` (from `fix/map-orders-001`).
5. Stop `ng serve` if it runs (L-2026-011). Do not start or stop the Entry/Engine servers the user runs.

## Step 1 — workflow ledger (before editing code)
```
node tools/feature-workflow/workflow.mjs init work-items/BROWSER-FIX-001/run.json . BROWSER-FIX-001 GVRP_Entry_API/src GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/BROWSER-FIX-001/run.json ready work-items/BROWSER-FIX-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/BROWSER-FIX-001/run.json ready work-items/BROWSER-FIX-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/BROWSER-FIX-001/run.json ready work-items/BROWSER-FIX-001/03-proof.json
```
Each must print a new state (`discover` → `design` → `implement`); on failure stop and report.

## Step 2 — baseline
- `GVRP_Entry_API` (JDK 21, L-2026-007): `mvnw.cmd -B clean test`. Record run / failures / errors / skipped and every failing test by name. If the two `@SpringBootTest` classes fail on schema validation, record the message and use `mvnw.cmd -B clean test "-Dsurefire.failIfNoSpecifiedTests=false" "-Dtest=!GvrpEntryApiApplicationTests,!DepotMapperTest"` as the baseline.
- `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` and `npm run build`. Record counts, failing specs by name, build warnings.
- **Text-assertion check (record the result):** search `GVRP_Entry_API/src/test` for assertions on the `ComplianceChecker` detail texts (`"Stop is in zone"`, `"Leg crosses zone"`, `"decides transit through"`), on `"No compliant vehicle for zone"`, and on `"Resource conflict."` / `RESOURCE_CONFLICT` message checks of `OptimizationJobService`; search `GVRP_Entry_client_v2/src` specs for `"Check capacity, time windows"` and `"Total Demand"`. An assertion that the design would break (exact old text) → **stop and report** before implementing; `contains("Z1")`-style checks that still pass are fine (see `context.md`).

## Allowed changes
Entry (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`):
- `dto/request/OrderInputDTO.java` (E1: the initializer only)
- `exception/GlobalExceptionHandler.java` (E2: one new handler)
- `dto/response/VehicleDTO.java`, `mapper/VehicleMapper.java` (E3)
- `service/OptimizationJobService.java` (E4: the invalid-status message only)
- `service/OptimizationCallbackService.java` (E5: delete the duplicate line only)
- `service/lez/ComplianceChecker.java` (E6: `label` + six detail strings), `service/lez/RouteComplianceService.java` (E6: `noCompliantVehicleReason` only)
- `service/lez/ZoneService.java` (E7: `createZone` COPIED name, `updateZone`, private helpers), `dto/request/BranchZoneUpdateRequest.java` (E7)
- New test classes for BF-T1…BF-T9 under `GVRP_Entry_API/src/test/java/...` (new files only)

Client (`GVRP_Entry_client_v2/src/app/`):
- `features/main/orders/components/edit-orders-dialog/edit-order-dialog.component.ts` (C1)
- `features/main/orders/orders-section-view/orders-section.component.{ts,html,scss}` (C2)
- `features/main/jobs/route-planning-dialog/route-planning-dialog.component.{html,ts}` (C3; `.ts` only for the pipe import)
- `features/admin/vehicles/vehicles-admin.component.html` (C4)
- `features/main/jobs/job-monitoring-dialog/job-monitoring-dialog.component.{html,scss}` (C5)
- `features/admin/vehicles/vehicle-permits-dialog/vehicle-permits-dialog.component.{html,scss}` (C6)
- `core/services/lez-api.service.ts` (C7: `renameZone`), `features/admin/zones/zone-editor-dialog/zone-editor-dialog.component.{ts,html,scss}` (C7)
- New spec files for BF-C1…BF-C7

Work item: `work-items/BROWSER-FIX-001/implementation.md`.

## Forbidden
- Editing, renaming, disabling, skipping, focusing or excluding any existing test, fixture or helper (Java or client), including `ZoneServiceTest`, `OptimizationCallbackServiceLezTest`, `ComplianceCheckerTest`, `LezFixtures` and every existing `*.spec.ts`. If an existing test fails because of this change, stop and report the test, the failure and the minimal change you propose.
- Any other file; `pom.xml`, `package*.json`, `angular.json`, `tsconfig*`, `public/**`, `SecurityConfig`, `application*.properties`, migrations or any schema change, the Engine API, `AGENTS.md`, `docs/`; any new dependency.
- Changing order status transitions, the plan request, D4, the LEZ compiler/checker logic (only texts), the send guard, MAP-ORDERS-001 behaviour, or anything listed under Exclusions in `request.md`.
- Logging customer data, phone numbers, rejected values, coordinates, tokens or secrets.
- Committing, pushing, rewriting history; INSERT/UPDATE/DELETE/DDL against `gvrp_db` (a live check through the UI is allowed only if you record every row it created).

## Step 3 — implement per `design.md`, then verify
Order: E1–E2 → E3–E5 → E6 → E7 → run the Entry tests → C1 → C2 → C3–C6 → C7.
- Entry: the Step 2 command; all green; count = baseline + new; list the new test names.
- Client: `npx ng test --watch=false --browsers=ChromeHeadless` all green, count = baseline + new; `npm run build` EXIT=0, no ERROR, no new budget warning compared with the baseline.
- Static: `git status --short` shows only allowed files; `git diff --stat`.
- Optional live check (stack already running, planner account the user names): Add Order; Select All Orders; plan; Recheck text; copy + rename a zone. Record what you saw and every row created. Not required.

## Step 4 — record and hand off
Write `work-items/BROWSER-FIX-001/implementation.md` with sections: Scope, Baseline (both modules, and the Step 2 text-assertion check), Changes Made (per E/C item), Commands Executed (with exit codes, incl. Steps 0–1), Verification Results, Modified Tests (must be "none"), Security-Relevant Changes (E2 logging, E7 tenant scoping: every lookup stays branch-scoped), Deviations From Design, Remaining Uncertainty (browser behaviour NOT_VERIFIED unless you ran the live check; stored texts of past solutions unchanged; E1 PUT semantics), Lesson Candidates ("none" if none), Handoff to Reviewer.
Then:
```
node tools/feature-workflow/workflow.mjs step work-items/BROWSER-FIX-001/run.json ready work-items/BROWSER-FIX-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done.
