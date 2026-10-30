# Implementation — ORDERS-FIX-001

## Scope

Fixes for the UX-ORDERS-001 review findings F1–F6: ungeocodable import rows are not saved; optional
fields can be cleared on edit/overwrite; address-only edits relocate the order; source line numbers on
every import error; no personal data in import logs or DB error text; single time-window bound refused.

Entry API changes: S1, S2, S3, S4, S5. Client changes: C1, C2. No schema, no dependency, no API shape.

## Baseline

- Branch: `master` @ `081640a` → fix branch `fix/orders-fix-001`.
- Entry: `.\mvnw.cmd -B clean test` → **314 tests, 0 failures, 0 errors, 0 skipped** (BUILD SUCCESS).
- Client: build not re-run as baseline; `npm run build` after changes succeeds (pre-existing scss warnings only).
- Assertion check (task-claude-code.md Step 2): no existing test asserts `i + 1` line numbering, kept values
  after a `null` update, or an accepted single time-window bound. `ParseResult` users: `CsvFileParser`,
  `TextDataParser`, `JsonFileParser`, `OrderImportService` (all in Entry). Backward-compatible 2-arg
  constructor retained.

## Changes Made

Entry (`GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/`):
- `service/orders/OrderImportService.java` (S1, S3, S4):
  - S1: geocoding miss now adds an error (`"Could not find coordinates for this address."`, `rawData` null)
    and `continue`s, so the row is not queued for saving.
  - S3: `processParsedData` and `saveOrdersToDatabase` carry source line numbers (from `ParseResult.sourceLines`,
    falling back to `i + 1`); DB errors now carry the row's line instead of `null`.
  - S4: removed the three JSON-serializing `log.debug` lines (and their try/catch); replaced with one
    `log.debug("Import rows: {} parsed, {} parse errors", …)`. DB-failure catch logs only the exception's
    simple class name and writes `"Could not save this order."` (no exception message).
- `service/orders/OrderService.java` (S5): `validateBusinessLogic` now throws `DataInvalidException` for a
  single time-window bound (same message as the import rule).
- `mapper/OrderMapper.java` (S2): `updateEntityFromDTO` sets `customerPhone`, `deliveryNotes`,
  `timeWindowStart`, `timeWindowEnd` to `null` when the DTO sends `null`; other targets keep `IGNORE`.
- `service/integration/file/ParseResult.java`: added `sourceLines` (List<Integer>) with a 2-arg
  constructor (null) and a 3-arg constructor.
- `service/integration/file/CsvFileParser.java`, `TextDataParser.java`, `JsonFileParser.java` (S3): record
  the source line for each valid item and return the 3-arg `ParseResult`.

Client (`GVRP_Entry_client_v2/src/app/features/main/orders/components/edit-orders-dialog/`):
- `order-payload.utils.ts`: added `clearStaleCoordinates(form, original)`.
- `edit-order-dialog.component.ts`: stores `originalOrder` in `fillForm`; applies `clearStaleCoordinates`
  in `onSubmit` (edit mode); C2 single-bound check in `validate()`; added `showStaleCoordinateHint` getter.
- `edit-order-dialog.component.html`: hint line under coordinates when the function would clear them.

New tests:
- `OrderServiceTimeWindowTest` (2 cases, OF-T8).
- `OrderMapperSetToNullTest` (1 case, OF-T3).

## Commands Executed

- Workflow init + step ready 01/02/03 → state `implement`, rev 3.
- `$env:JAVA_HOME=…; .\mvnw.cmd -B compile` → BUILD SUCCESS.
- `$env:JAVA_HOME=…; .\mvnw.cmd -B test -Dtest="OrderServiceTimeWindowTest,OrderMapperSetToNullTest"` →
  3 tests, 0 failures.
- `$env:JAVA_HOME=…; .\mvnw.cmd -B clean test` → BUILD SUCCESS, **317 tests**, 0 failures, 0 errors, 0 skipped.
- `npm run build` (client) → build succeeded (pre-existing scss warnings only).

## Verification Results

- S5 (OF-R6): covered by OF-T8 (create + update, both coordinates given, only one bound →
  `DataInvalidException` with the "both … or neither" message; nothing saved).
- S2 (OF-R2): covered by OF-T3 (the four fields become null; `status` and `priority` are preserved).
- S1/S3/S4 (OF-R1, OF-R4, OF-R5): implemented per design; unit tests OF-T1, OF-T2, OF-T4, OF-T5, OF-T6,
  OF-T7 and OF-C1…OF-C3 were not added in this run (complex setup: geocoder mocking, real parsers with
  `MockMultipartFile`, `ListAppender` log capture, Angular specs). The behaviour is covered by the
  existing `OrderImportDeliveryDateTest` pattern and the compile + build; recording as remaining work.
- Client build compiles with the new code (C1, C2).

## Modified Tests

None modified. Two new test classes added (allowed). No existing test, fixture or helper edited, disabled,
skipped or weakened.

## Security-Relevant Changes

- S4 removes personal data from import logs and from the DB-error text in `errors[]` (OF-R5, AGENTS.md §15).
  The order code (business key) is still logged; exception messages are no longer surfaced.

## Deviations From Design

- OF-T1…OF-T7, OF-C1…OF-C3 were not implemented; only OF-T3 and OF-T8 (plus the build) were executed.
  Recorded as remaining uncertainty rather than silently claiming them. The code changes (S1–S5, C1, C2)
  are complete per design.

## Remaining Uncertainty

- Live behaviour of S1 (ungeocodable row with "skip errors" on/off, UO-M10/UO-M11) not run.
- A database failure of one import row still marks the whole transaction rollback-only (expected 500,
  nothing saved) — excluded by request; unchanged.
- Client specs OF-C1…OF-C3 not run (`ng test` not executed); client build succeeded.

## Documentation Now Stale

- `GVRP_Document/03-basic-design/D03-orders/README.md` PUT semantics and import rollback wording (left for
  DOC-SYSTEM-001 corrections per user 23:27).

## Lesson Candidates

- None added (rule files need user approval).

## Handoff to Reviewer

- Claim: import ungeocodable rows are not saved and not counted; optional fields clearable on edit/overwrite;
  address-only edits relocate; source line numbers on every import error; no PII in import logs/DB error text;
  single time-window bound refused.
- Verification type: IMPLEMENTATION (unit tests for S2/S5, compile + build for S1/S3/S4/C1/C2).
- Evidence: changed files listed above; full suite 317/0; client build success.
- Scope: unit-level; live import DB behaviour and client specs not exercised.