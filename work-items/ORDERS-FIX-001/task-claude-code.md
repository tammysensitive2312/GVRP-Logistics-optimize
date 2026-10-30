# Task for Claude Code — ORDERS-FIX-001

You are the implementing engineer. Implementation is authorized by the user (2026-10-30 23:27 +07:00, "bạn viết work-items để fix-bug tôi sẽ giao cho claude code làm"). Follow `AGENTS.md` in full (sections 14, 15, 16) and `CLAUDE.md`. Run every command yourself from the repository root, on Windows (L-2026-014); run Maven through PowerShell with `JAVA_HOME` set to JDK 21 for the command (L-2026-007).

Run order: DEPOT-FIX-001 → **ORDERS-FIX-001** → LEZ-FIX-001, one at a time.

## Read first
1. `work-items/ORDERS-FIX-001/request.md`  2. `context.md`  3. `design.md` (**the specification**)
4. `work-items/UX-ORDERS-001/review.md`, `design.md`, `implementation.md`
5. `docs/engineering/LESSONS.md`: L-2026-001, -003, -007, -009, -010, -011, -014
If the design is wrong or impossible, stop and report; do not redesign.

## Step 0 — preconditions and branch
1. `node tools/feature-workflow/workflow.mjs status work-items/DEPOT-FIX-001/run.json` → state `verify` (or later); otherwise stop and ask the user.
2. The user commits `fix/depot-fix-001` first. Then `git switch master`, `git log -1 --oneline` (record), `git status --short`. Allowed uncommitted: `work-items/ORDERS-FIX-001/`, `work-items/LEZ-FIX-001/`, `work-items/*/review.md`, `.claude/`, `.mcp.json`, `*.log`. Anything else → stop and report.
3. `node tools/session-brief.mjs` — include the output.
4. `git switch -c fix/orders-fix-001` (from `master`; it does not depend on the depot fix).
5. Do not stop the user's servers; if `ng serve` or the Entry API cannot be stopped, record it.

## Step 1 — workflow ledger
```
node tools/feature-workflow/workflow.mjs init work-items/ORDERS-FIX-001/run.json . ORDERS-FIX-001 GVRP_Entry_API/src GVRP_Entry_client_v2/src
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json ready work-items/ORDERS-FIX-001/01-proof.json
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json ready work-items/ORDERS-FIX-001/02-proof.json
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json ready work-items/ORDERS-FIX-001/03-proof.json
```

## Step 2 — baseline
- Entry: `.\mvnw.cmd -B clean test` (record counts and any pre-existing failure by name).
- Client: `npx ng test --watch=false --browsers=ChromeHeadless` (last recorded 435 SUCCESS after UX-LEZ-001), `rm -rf dist && npm run build` (12 warnings).
- **Assertion check (record):** search tests/specs for `lineNumber`, `getLineNumber`, `line_number`, `i + 1`, `"The coordinates cannot be obtained"`, `"Lỗi lưu DB"`, `Raw Orders`, `Time Window must have both`, `timeWindowStart`, `updateEntityFromDTO`, `NullValuePropertyMappingStrategy`, `ParseResult`, `clearStaleCoordinates`, `toOrderPayload`, `fillForm`. An existing assertion the design would break (for example a test expecting `i + 1` numbering, a kept value after a `null` update, or an accepted single time-window bound) → **stop and report**.
- Also list every other user of `ParseResult` (vehicles or other imports) — they must compile unchanged.

## Allowed changes
Entry (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`): `service/orders/OrderImportService.java` (S1, S3, S4), `service/orders/OrderService.java` (S5 only), `mapper/OrderMapper.java` (S2 only), `service/integration/file/ParseResult.java`, `CsvFileParser.java`, `TextDataParser.java`, `JsonFileParser.java` (S3 only); new test classes.
Client (`GVRP_Entry_client_v2/src/app/features/main/orders/components/edit-orders-dialog/`): `order-payload.utils.ts`, `edit-order-dialog.component.{ts,html,scss}`; new spec files.
Work item: `work-items/ORDERS-FIX-001/implementation.md`.

## Forbidden
- Editing, disabling, skipping, focusing or excluding any existing test, fixture or helper. A failing existing test → stop and report.
- Any other file: `GVRP_Document/**` and other documentation (user 23:27), `BaseMapperConfig`, `pom.xml`, `package*.json`, `angular.json`, `application*.properties`, `SecurityConfig`, migrations, Engine; new dependencies.
- New `@SpringBootTest` classes (L-2026-001).
- Logging customer name, phone, address or coordinates; putting exception messages in `errors[]`; showing import `raw_data` in the UI.
- Changing the transaction boundaries of the import (design §4).
- Committing, pushing; DB writes other than an optional live check through the UI (record every row created).

## Step 3 — implement per `design.md`, then verify
Order: S1 → S5 → S2 → S3 → S4 → Entry tests OF-T1…T8 → C2 → C1 → client specs OF-C1…C3.
- Entry all green, count = baseline + new; client all green, count = baseline + new; build EXIT 0, no new warning.
- `git status --short` shows only allowed files.
- Optional live check: UO-M5, UO-M6, UO-M10, UO-M11 of `work-items/UX-ORDERS-001/review.md` (expected results are now the "after the fix" ones); record order codes created.

## Step 4 — record and hand off
`work-items/ORDERS-FIX-001/implementation.md`: Scope, Baseline (incl. assertion check and `ParseResult` users), Changes Made, Commands Executed (exit codes), Verification Results, Modified Tests ("none"), Security-Relevant Changes (S4 logging, error texts), Deviations From Design, Remaining Uncertainty (import DB failure still rolls back the whole file; live geocoding NOT_VERIFIED unless run), Documentation Now Stale (D03 PUT and import wording — not updated by decision of 23:27), Lesson Candidates, Handoff to Reviewer. Then:
```
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json ready work-items/ORDERS-FIX-001/04-proof.json
```
Do not go further than `verify`. Tell the user you are done, in Vietnamese.
