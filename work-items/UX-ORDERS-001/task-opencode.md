# Task for the OpenCode engineer — UX-ORDERS-001

You are `gvrp-engineer`. Implementation is authorized by the user (2026-10-29 22:18 +07:00, "đồng ý, bạn viết đi"). Follow `AGENTS.md` in full (sections 14, 15, 16). Run every command yourself from the repository root, on Windows (L-2026-014); run Maven through PowerShell with `JAVA_HOME` set to JDK 21 for the command (L-2026-007).

## Read first
1. `work-items/UX-ORDERS-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `work-items/BROWSER-FIX-001/review.md` (F1)
5. `docs/engineering/LESSONS.md`: L-2026-001, -003, -007, -009, -010, -011, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/feature-workflow/workflow.mjs status work-items/BROWSER-FIX-001/run.json` → state `verify`; otherwise stop.
2. The user commits `fix/browser-fix-001` first. On it: `git log -1 --oneline`, `git status --short`; allowed uncommitted: `work-items/UX-ORDERS-001/`, `work-items/UX-LEZ-001/`, `work-items/BROWSER-FIX-001/review.md`, `work-items/BROWSER-FIX-001/test-scenarios.md`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report.
3. `node tools/session-brief.mjs` — include the output.
4. `git switch -c feat/ux-orders-001` (from `fix/browser-fix-001`).
5. Do not stop the user's servers; if `ng serve` cannot be stopped, record it (as in BROWSER-FIX-001).

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/UX-ORDERS-001/run.json . UX-ORDERS-001 GVRP_Entry_API/src GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/UX-ORDERS-001/run.json ready work-items/UX-ORDERS-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/UX-ORDERS-001/run.json ready work-items/UX-ORDERS-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/UX-ORDERS-001/run.json ready work-items/UX-ORDERS-001/03-proof.json
```

## Step 2 — baseline
- Entry: `.\mvnw.cmd -B clean test` (expect 298/0/0/0 from BROWSER-FIX-001; record what you get).
- Client: `npx ng test --watch=false --browsers=ChromeHeadless` (expect 385), `rm -rf dist && npm run build` (12 warnings).
- **Assertion check (record):** search tests/specs for `"Coordinates are required"`, `latitude: 0`, `order_template.csv`, `.xlsx`, `OrderImportService`, `CsvFileParser`, `Goong`, `onImportClick`, `importOrders`. An existing assertion the design would break → **stop and report**.

## Allowed changes
Entry (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`): `service/orders/OrderService.java` (S1), `service/orders/OrderImportService.java` (S2: the one line), `dto/request/OrderImportRequest.java` (S2 annotation), `service/integration/file/CsvFileParser.java` (S2 columns), `service/GeocodingService.java` (S3 log lines only); new test classes.
Client (`GVRP_Entry_client_v2/src/app/`): `core/models/order.model.ts` (new types only), `core/services/api.service.ts` (`importOrders`, widened parameter types), `features/main/orders/components/edit-orders-dialog/edit-order-dialog.component.{ts,html,scss}` + new `order-payload.utils.ts`, `features/main/orders/components/import-orders-dialog/import-orders-dialog.component.{ts,html}`, `features/main/orders/orders-section-view/orders-section.component.{ts,html,scss}`; new spec files.
Work item: `work-items/UX-ORDERS-001/implementation.md`.

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture or helper. A failing existing test → stop and report.
- Any other file; `pom.xml`, `package*.json`, `angular.json`, `application*.properties`, `SecurityConfig`, migrations, Engine; new dependencies; Excel parsing.
- Logging addresses, customer data, coordinates, the geocoding URL or key; showing import `raw_data` in the UI.
- Committing, pushing; DB writes (a live check through the UI is allowed if you record every row created).

## Step 3 — implement per `design.md`, then verify
Order: S1 → S2 → S3 → Entry tests → C1 → C2 → C3.
- Entry all green, count = baseline + new; client all green, count = baseline + new; build EXIT 0, no new warning.
- `git status --short` shows only allowed files.
- Optional live check: Add Order without coordinates (real address in Hà Nội), Import the downloaded template on today's date; record results and rows created.

## Step 4 — record and hand off
`work-items/UX-ORDERS-001/implementation.md`: Scope, Baseline (incl. assertion check), Changes Made, Commands Executed (exit codes), Verification Results, Modified Tests ("none"), Security-Relevant Changes (S3 logging, import errors without raw data), Deviations From Design, Remaining Uncertainty (live geocoding NOT_VERIFIED unless run), Lesson Candidates, Handoff to Reviewer. Then:
```
node tools/feature-workflow/workflow.mjs step work-items/UX-ORDERS-001/run.json ready work-items/UX-ORDERS-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done.
