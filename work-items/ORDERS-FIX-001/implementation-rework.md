# Implementation (rework) — ORDERS-FIX-001

Engineer: Claude Code, 2026-10-31, Windows host. Branch `fix/orders-fix-001` (from `master` @ `081640a`), uncommitted. Reason: `review.md` R1–R5. `implementation.md` is left unchanged (evidence of revision 4).

## Findings Addressed

| ID | Fix | Evidence |
|---|---|---|
| R1 | Restored both strings in `OrderImportService.java` byte-for-byte from `master`: `"Định dạng file không hỗ trợ (chỉ CSV/JSON)."` (line 101) and `"Lỗi hệ thống khi đọc file: {}"` (line 121). Written by a Python script reading the two lines from `git show master:<file>`, UTF-8 **without BOM** (first bytes `70 61 63` = `pac`), CRLF line endings kept | `git diff master -- …/OrderImportService.java` shows no change to either line; encoding scan below |
| R2 | Added the missing Entry tests OF-T1, OF-T2, OF-T4, OF-T5, OF-T6, OF-T7 (7 tests) and client specs OF-C1, OF-C2, OF-C3 (11 tests) | `OrderImportRowsTest` 7/7; Karma 446 = 435 + 11 |
| R3 | Client suite run before (baseline) and after the change | 435 → 446 SUCCESS, no failure |
| R4 | `showStaleCoordinateHint` now returns `this.isEditMode && clearStaleCoordinates(this.form, this.originalOrder) !== this.form`; the duplicated rule is gone. Also restored the doc comment of `isBlankCoordinate` that the first run had dropped, and documented `clearStaleCoordinates` | `edit-order-dialog.component.ts`, `order-payload.utils.ts`; OF-C2 checks hint and payload agree |
| R5 | OF-T8: both cases now assert `verify(orderRepository, never()).save(any())`; the update case asserts the message *Time Window must have both start and end times, or neither.* | `OrderServiceTimeWindowTest` 2/2 |

## Commands Executed

| Step | Command | Exit / result |
|---|---|---|
| 0 | `git branch --show-current` | `fix/orders-fix-001` |
| 0 | `workflow.mjs step … code_failed 05-proof-code-failed.json` | 0, `implement` rev 5 |
| 1 | Python rewrite of the two lines (see R1) | `fixed 2` |
| 1 | encoding scan (PowerShell, below) | 0 hits |
| 2 | `.\mvnw.cmd -B test "-Dtest=OrderImportRowsTest,OrderServiceTimeWindowTest"` (PowerShell, `JAVA_HOME` = JDK 21) | 0; 9 tests, 0 failures, 0 errors |
| 3.1 | `npx ng test --watch=false --browsers=ChromeHeadless` (baseline, current code incl. C1/C2) | 0; **435 SUCCESS** |
| 3.4 | `npx ng test --watch=false --browsers=ChromeHeadless` | 0; **446 SUCCESS** |
| 3.4 | `rm -rf dist && npm run build` | 0; 0 ERROR; 12 warnings, same set as the UX-LEZ-001 baseline (sorted text diff) |
| 4 | `.\mvnw.cmd -B clean test` | 0; **324 tests**, 0 failures, 0 errors, 0 skipped (317 + 7) |
| 4 | `git status --short` | only the allowed files (below) |
| 5 | `workflow.mjs step … ready 06-proof.json` | result in the handoff message |

## Encoding Scan Output

The pattern given in the task could not be typed literally: Windows PowerShell 5.1 reads the command text in the ANSI code page, so `'Ã|Ä‘|á»|â€'` reached the parser already mangled (`á» : The term 'á»' is not recognized…`, 10 times). The scan was re-run with the same pattern built from code points:

```
pattern codepoints: U+00C3 U+007C U+00C4 U+2018 U+007C U+00E1 U+00BB U+007C U+00E2 U+20AC
--- tracked changes:
files scanned: 10
--- untracked:
files scanned: 2
--- end
```

0 hits in the 10 tracked changed files and the 2 untracked source files present at the time (`OrderMapperSetToNullTest.java`, `OrderServiceTimeWindowTest.java`). Positive control: a scratch file containing `Định` encoded UTF-8 → read as Windows-1252 → written as UTF-8 gave **1 hit** with the same pattern, so the scan detects this corruption. The files added later in this rework were written with the editor tools (UTF-8).

## Client Baseline

435 SUCCESS, 0 failures on the current code (with the first run's C1/C2 change). No existing spec failed, so nothing to stop for.

## Tests Added

Entry — `GVRP_Entry_API/src/test/java/org/truong/gvrp_entry_api/service/orders/OrderImportRowsTest.java` (real `TextDataParser`, `CsvFileParser`, `JsonFileParser`, real validator; Mockito for repositories, mapper, geocoder; Logback `ListAppender` at `DEBUG` on `OrderImportService`):
- `ofT1_ungeocodableRowWithSkipOn_isReportedOnceAndNotSaved`
- `ofT2_ungeocodableRowWithSkipOff_refusesTheWholeImport`
- `ofT4_csvErrorsCarryTheFileLine_andTheValidRowIsImported` (errors at lines 2 and 3, row at line 4 imported)
- `ofT5_textWithABlankFirstLine_reportsTheRealLine` (line 2)
- `ofT6_jsonErrorCarriesTheLineOfItsObject_notItsIndex` (line 3, index would give 2)
- `ofT7_importLogsAndErrorsCarryNoCustomerData`
- `ofT7_databaseFailureIsGeneric_andCarriesTheRowLine` (`save` throws `RuntimeException("Duplicate entry 'Nguyen Van A' …")`; error message `"Could not save this order."`, field `Database`, line 2; log carries only `RuntimeException`)

OF-T7 checks every log message, every log argument, any throwable message, and every `errors[]` message and raw data against the customer names, phones, both addresses, both coordinates and the exception text.

Entry — `OrderServiceTimeWindowTest` (added in the first run, untracked): R5 assertions added to its two cases.

Client — `GVRP_Entry_client_v2/src/app/features/main/orders/components/edit-orders-dialog/`:
- `order-payload-stale.utils.spec.ts` (OF-C1, 5): same address; changed address + loaded coordinates → cleared; changed address + new coordinates; one coordinate typed as text; whitespace-only change.
- `edit-order-dialog-relocate.component.spec.ts` (OF-C2 4, OF-C3 2): hint hidden → shown after an address-only change, `updateOrder` called with `latitude: null, longitude: null`; picked location kept without hint; unchanged address keeps coordinates; no hint in add mode; only start time on edit and only end time on create → toast *Enter both start and end time, or leave both empty.*, no API call.

## Modified Tests

None of the existing tests (on `master`). `OrderServiceTimeWindowTest.java` was created by the first run of this work item (untracked, not on `master`) and was strengthened as R5 requires.

## Remaining Uncertainty

- Live behaviour (OF-M1…OF-M6 of `review.md`): NOT_VERIFIED; no browser or API run.
- A database failure of one import row still marks the import transaction rollback-only (expected `500`, nothing saved); OF-T7's DB case runs without a transaction, so it shows the error entry, not that rollback (design §4, excluded).
- The encoding scan covered the files changed at the time of the scan; the four test files added afterwards were written through the editor in UTF-8 and were not re-scanned.

## Lesson Candidate (proposal only)

Windows PowerShell 5.1 corrupts non-ASCII text twice: `Get-Content`/`Set-Content` default to the ANSI code page (the R1 mojibake), and a command line passed to `powershell.exe` is itself decoded in that code page, so a literal pattern such as `'á»'` in a `Select-String` command breaks the parser. Edit files with the editor tools or `[IO.File]::WriteAllText(path, text, (New-Object Text.UTF8Encoding $false))`, and build non-ASCII search patterns from code points (`[char]0x00E1`). Candidate addition to `docs/engineering/LESSONS.md` (needs approval).

## Handoff to Reviewer

- Re-run: `cd GVRP_Entry_API; .\mvnw.cmd -B clean test` (324/0/0/0, JDK 21); `cd GVRP_Entry_client_v2; npx ng test --watch=false --browsers=ChromeHeadless` (446) and `npm run build` (12 warnings).
- Read first: `OrderImportRowsTest` (OF-T7 assertions), `edit-order-dialog-relocate.component.spec.ts`, the R1 lines of `OrderImportService.java`.
- Manual scenarios OF-M1…OF-M6 in `review.md` remain for the user; record every order code created.
