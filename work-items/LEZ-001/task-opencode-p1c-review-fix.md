# Task for the engineer — LEZ-001, P1c review fix (R1, R2, R3, R5, R6)

You are `gvrp-engineer` (or a Claude Code session acting as the engineer). Implementation of P1 is authorized by the user (2026-10-26 22:54 +07:00). This task fixes findings **R1, R2, R3, R5 and R6** of `review-p1c.md` and nothing else. The user asked for it on 2026-10-29 00:06 +07:00 ("có bạn viết đi"). Follow `AGENTS.md` in full (sections 14, 15 and 16). Run every command yourself from the repository root.

Client only. The Entry API, the Engine API and P1b behaviour are not touched. R4, R7, R8 and R9 stay open.

## Read first
1. `work-items/LEZ-001/review-p1c.md` §4 (R1, R2, R3, R5, R6)
2. `work-items/LEZ-001/design-p1c.md` §3.4, §3.6, §3.8, §3.11, §3.12
3. `work-items/LEZ-001/implementation.md` §P1c (C7, C8, C9) and §P1c follow-up
4. `docs/engineering/LESSONS.md`: L-2026-003, -009, -011

If a fix below is wrong or impossible, stop and report. Do not redesign.

## Facts the fixes rely on (verified by the reviewer in the code)
- `ComplianceChecker.check` (Entry): `ZONE_ACCESS` and the stop variant of `LEZ_DATA_MISSING` carry `seq = i`, the plan index of the **stop**; `ZONE_TRANSIT`, `LEZ_GEOMETRY_MISSING` and the leg variant of `LEZ_DATA_MISSING` carry `seq = i`, the **departing stop** of the leg `i → i+1`. The leg variant of `LEZ_DATA_MISSING` is recognisable only by its detail text ("…transit through…").
- `RouteSendService.enforceLezCompliance` calls `fillMissingLegs` (engine `/legs`) **before** the check, so a route whose only problem is missing geometry can pass the send guard.
- `errorInterceptor` re-throws `{ message, status, originalError }`; `serverErrorMessage` (`shared/utils/api-error.utils.ts`) reads both that wrapper and a raw `HttpErrorResponse` body.
- `MapService.displayRoute` draws **one** polyline per route and sorts the stop markers by arrival time, not by plan index.

## Step 0 — preconditions
1. Run `node tools/session-brief.mjs` and include the output.
2. Run `git status --short` on `feature/lez-001`. Expected: the P1a/P1b/P1b-fix/P1c/P1c-follow-up changes, `work-items/LEZ-001/*`, `docs/engineering/LESSONS.md`, `docs/security/SECURITY_CHECKLIST.md`, logs. Anything else → stop and report.
3. Run `node tools/feature-workflow/workflow.mjs recover work-items/LEZ-001/run.json`. State must be `implement`. Warnings on `work-items/LEZ-001/*` are expected.
4. Stop `ng serve` if it runs (L-2026-011).

## Step 1 — baseline
From `GVRP_Entry_client_v2`: `npx ng test --watch=false --browsers=ChromeHeadless` (expected 323 SUCCESS) and `npm run build` (EXIT=0). Record counts and every failing spec by name. The Entry and Engine modules are not touched; do not run them.

## What to change

**F-R1. Geometry-only violations do not block the send button** (`route-view.component.ts`, `.html`).
- In `sendBlockedReason`: a `VIOLATION` row whose violations are **all** `LEZ_GEOMETRY_MISSING` returns `null` (send allowed). Any other code in the list keeps today's blocking text. An empty violation list keeps today's text.
- For that case only, show a short note on the card next to the send button: "Road geometry is fetched and checked when you send." (new method, e.g. `sendNote(route)`; no toast).
- The compliance panel is unchanged.

**F-R2. Show the server's refusal text** (`route-view.component.ts`).
- In the send `error` handler, `describeEvaluateError` and `describeSaveError`, replace the `error.error.errors[0].message` lookup with `serverErrorMessage(error, <the current fallback text>)`.
- Keep every status branch and its order exactly (409 still sets `saveConflict` and appends " Load the latest revision to continue."; 0/502 still answer the "unreachable" text before any message lookup; 422 on send still emits `complianceRecheckNeeded`).

**F-R3. Highlight the offending leg or stop, not the whole route** (`shared/services/map.service.ts`, `lez-compliance-panel/compliance-panel.component.ts`, `shared/utils/route-geometry.utils.ts`).
- New signature `focusLeg(routeId: number, seq: number, code: string, detail?: string | null)`. The panel passes the violation's `code` and `detail` (extend `ComplianceViolationRow`).
- Leg codes = `ZONE_TRANSIT`, `LEZ_GEOMETRY_MISSING`, and `LEZ_DATA_MISSING` whose detail contains "transit": highlight the leg `seq → seq+1`. Stop codes = `ZONE_ACCESS` and any other `LEZ_DATA_MISSING`: highlight the stop `seq` and the leg that reaches it (`seq−1 → seq`) when `seq > 0`.
- Index stops and legs by **plan order** (`orderStops(route.stops)`, the order the panel uses), kept per route in the focus registry; the arrival-time order of the drawn markers stays as today.
- Leg geometry for the highlight: when the route was drawn from stored geometry, decode that leg's own polyline (keep the per-leg paths from `routeGeometry`; add a pure helper such as `storedLegPaths(geometry)` next to `completeRoutePolyline`); otherwise a straight segment between the two stops.
- Draw the highlight in a separate layer (red `#b42318`, weight 8). Remove the previous highlight on the next `focusLeg` and in `clearRoutes`. The route polyline itself keeps its colour.
- Fit the view to the highlighted leg; for a stop, centre on it (zoom ≥ 15) and open its marker popup.

**F-R5. Permit dates are required on edit too** (`vehicle-permits-dialog.component.ts`, `.html`).
- `validate()` requires both `valid_from` and `valid_to` for create **and** edit. Keep the one-year default for a new permit. Update the hint text accordingly.

**F-R6. The rule form requires "Effective from"** (`features/admin/zones/zone-rule-form.utils.ts`).
- `validateRuleForm` adds `"Effective from" is required.` when `effectiveFrom` is blank.

## Tests (client; new `it` blocks; DB-free, no real HTTP)

| ID | File | Case | Expected |
|---|---|---|---|
| FR1-T1 | `route-view-lez.component.spec.ts` | VIOLATION with only `LEZ_GEOMETRY_MISSING` (two legs) | send enabled, `sendBlockedReason` null, note text rendered |
| FR1-T2 | same | VIOLATION with `LEZ_GEOMETRY_MISSING` + `ZONE_ACCESS` | send disabled, reason names `ZONE_ACCESS` |
| FR2-T1 | same | send fails with the **interceptor shape** `{ status: 422, message: 'ZONE_ACCESS seq=1 zone=Z17', originalError: { error: { errors: [{ message: 'ZONE_ACCESS seq=1 zone=Z17' }] } } }` | toast shows that text; `complianceRecheckNeeded` emitted |
| FR2-T2 | same | save fails with the interceptor shape, status 422 / status 409 | `saveError()` equals the server text / server text + " Load the latest revision to continue." and `saveConflict()` true |
| FR3-T1 | `route-geometry.utils.spec.ts` | `storedLegPaths` on a 3-leg geometry; one leg null | three decoded paths in plan order; null for the incomplete case (same rule as `completeRoutePolyline`) |
| FR3-T2 | new `map-focus.utils.spec.ts` or the same file | pure decision "leg or stop, which indices" for `ZONE_TRANSIT` seq 1, `LEZ_GEOMETRY_MISSING` seq 0, `ZONE_ACCESS` seq 2, `ZONE_ACCESS` seq 0, `LEZ_DATA_MISSING` with "transit" detail, without it | leg 1→2; leg 0→1; stop 2 + leg 1→2; stop 0 and no leg; leg; stop |
| FR3-T3 | `compliance-panel.component.spec.ts` | click a violation | `MapService.focusLeg` called with `(routeId, seq, code, detail)` |
| FR5-T1 | `vehicle-permits-dialog.component.spec.ts` | edit an existing permit and clear `valid_to` | validation error, no PUT |
| FR6-T1 | `zone-rule-form.utils.spec.ts` | blank `effectiveFrom` | error `"Effective from" is required.` |

Keep the Leaflet-free style of P1c: put decisions into pure functions and test those; `MapService` itself needs no new spec.

## Allowed changes
- `src/app/features/main/orders/route-view/route-view.component.{ts,html,scss}`
- `src/app/features/main/orders/route-view/route-view-lez.component.spec.ts` (new `it` blocks)
- `src/app/shared/services/map.service.ts`
- `src/app/shared/utils/route-geometry.utils.ts` (+ spec), one new pure utility file for the focus decision (+ spec) if you prefer it separate
- `src/app/features/main/orders/lez-compliance-panel/compliance-panel.component.{ts,html}` and its spec
- `src/app/features/admin/vehicles/vehicle-permits-dialog/vehicle-permits-dialog.component.{ts,html}` and its spec
- `src/app/features/admin/zones/zone-rule-form.utils.ts` and its spec; `zone-editor-dialog.component.spec.ts` **only** if an existing fixture there now fails R6 (see below)
- `work-items/LEZ-001/implementation.md` (new section `## P1c review fix`), `work-items/LEZ-001/checkpoint-p1c-review-fix.json`

**About the P1c spec files** (`route-view-lez`, `compliance-panel`, `vehicle-permits-dialog`, `zone-rule-form.utils`, `zone-editor-dialog`, `route-geometry.utils`): they are P1c's own, not yet accepted. Add tests freely. Change an existing assertion or fixture there **only** when this task's fix makes it wrong by design (expected: the `focusLeg` call arguments in `compliance-panel`; an edit-permit fixture without dates; a rule fixture without `effectiveFrom`). List every such change under Modified Tests with "review-p1c R<n>". Do not weaken any other assertion.

## Forbidden
- Any file outside the list, including every historical spec (the five D8 specs stay exactly as they are now), `error.interceptor.ts`, `api-error.utils.ts`, the Entry/Engine APIs, `package*.json`, `angular.json`, `public/**`.
- Findings R4, R7, R8, R9 and anything from review-p1b F2–F8.
- New dependencies; committing or pushing; writes to `gvrp_db`.

## Step 2 — verification
1. `npx ng test --watch=false --browsers=ChromeHeadless`: all green, count = 323 + new tests.
2. `npm run build`: EXIT=0, no ERROR; compare warnings with the baseline. `orders-table` is not in this task, so its 7.93 kB must not change.
3. Static: `git status --short` shows only allowed files; `router.project-osrm.org` still only in `routing.service.ts`.

## Step 3 — record and checkpoint
- Add `## P1c review fix` to `implementation.md` (Scope, Baseline, Changes Made, Commands Executed, Verification Results, Modified Tests, Remaining Uncertainty, Handoff).
- In §P1c "Changes Made" item 10 (C9), append one line: "focusLeg corrected by §P1c review fix (review-p1c R3)." Do not rewrite the rest of §P1c.
- Remaining Uncertainty must say: the map highlight and the send note are NOT_VERIFIED in a browser until the user's check; the P1b manual test is still postponed.
- Create `checkpoint-p1c-review-fix.json` (current run revision; `next_action`: "Claude re-reviews; user runs the P1c browser check and the P1b review §5 step 4") and run:
```
node tools/feature-workflow/workflow.mjs checkpoint work-items/LEZ-001/run.json work-items/LEZ-001/checkpoint-p1c-review-fix.json
```
No state transition. Tell the user you are done.
