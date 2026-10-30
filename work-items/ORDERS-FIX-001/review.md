# Review — ORDERS-FIX-001

Reviewer: Claude (cloud session), 2026-10-31 00:05 +07:00. Read through the device bridge on branch `fix/orders-fix-001` (HEAD verified in `.git/HEAD`; created from `master` @ `081640a` per `logs/HEAD`, working tree uncommitted): `implementation.md`, `run.json` (state `verify`, rev 4), `OrderImportService`, `OrderService` (validation), `OrderMapper`, `ParseResult`, `CsvFileParser`, `TextDataParser`, `JsonFileParser`, `OrderMapperSetToNullTest`, `OrderServiceTimeWindowTest`, client `order-payload.utils.ts`, `edit-order-dialog.component.{ts,html}`; file search of the Entry test tree for import tests.
Not re-run: Maven, Karma, build. `git status` / `git diff` not available from this session.

## Verdict

**Returned to implement (`code_failed`).** The production code follows the design and reads correctly (S1–S5, C1, C2), but:
1. one file was saved with a broken encoding, corrupting a user-facing message (R1);
2. 8 of the 11 designed tests were not written, including every test of the defect this item exists for (F1), and the client test suite was never run (R2).

AGENTS.md §10/§11: the change is not verified enough to hand to the user.

## Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| R1 | HIGH | VERIFIED by reading | **Mojibake in `OrderImportService.java`.** Two Vietnamese strings that were correct on `master` (read 2026-10-30 23:35) are now double-encoded: the 400 message for an unsupported file type reads `"Äá»‹nh dáº¡ng file khÃ´ng há»— trá»£ (chá»‰ CSV/JSON)."` (was `"Định dạng file không hỗ trợ (chỉ CSV/JSON)."`) and the file-read error log reads `"Lá»—i há»‡ thá»‘ng khi Ä‘á»c file: {}"` (was `"Lỗi hệ thống khi đọc file: {}"`). Typical cause: the file was read as Windows-1252 and written back as UTF-8 (Windows PowerShell `Get-Content`/`Set-Content` defaults). The other changed Java files and the client files keep their non-ASCII text intact (Vietnamese comments in `CsvFileParser`, `JsonFileParser`, `OrderMapper`; emoji in the dialog). Fix: restore both strings exactly; save as UTF-8 without BOM; scan every changed file for the markers `Ã`, `Ä`, `á»`, `â€`. |
| R2 | HIGH (verification gap) | VERIFIED | **Designed tests missing.** Written: OF-T3 (`OrderMapperSetToNullTest`), OF-T8 (`OrderServiceTimeWindowTest`). Missing: OF-T1, OF-T2 (F1 — the MEDIUM defect), OF-T4, OF-T5, OF-T6 (F4 line numbers), OF-T7 (F5 no personal data — AGENTS.md §15), OF-C1, OF-C2, OF-C3 (client). `implementation.md` says the behaviour "is covered by the existing `OrderImportDeliveryDateTest` pattern": that test covers none of these behaviours (two valid rows, geocoder always succeeds). The reason given ("complex setup") is not an environment blocker; `OrderImportDeliveryDateTest` already shows the whole setup (real parser + validator, mocks). |
| R3 | MEDIUM (verification gap) | VERIFIED | **Client tests never run.** Task Step 2 required the Karma baseline and the post-change run; `implementation.md` records neither (build only). `validate()` gained a check and the dialog gained a getter; the existing specs `edit-order-dialog-errors.component.spec.ts` and `edit-order-dialog-geocode.component.spec.ts` may now fail and nobody would know. |
| R4 | LOW | VERIFIED by reading | `showStaleCoordinateHint` re-implements the rule of `clearStaleCoordinates` instead of calling it; the two can drift. Suggest `return this.isEditMode && clearStaleCoordinates(this.form, this.originalOrder) !== this.form;`. |
| R5 | LOW | VERIFIED by reading | OF-T8 does not assert "nothing saved" (`verify(orderRepository, never()).save(any())`), and the update case does not check the message. |
| R6 | INFO | VERIFIED by reading | Correct as designed: S1 `continue` and address-free message; S2 per-field `SET_TO_NULL` with `status`/`priority` kept (OF-T3 proves it); S3 lines carried through parsers → validation → DB errors with `i + 1` fallback and a backward-compatible `ParseResult`; S4 three JSON debug dumps removed, DB error text generic, class name only in the log; S5 throw added; C1 `originalOrder` + `clearStaleCoordinates` in `onSubmit` + hint; C2 both one-sided cases. |

## Security

- S4 removes customer data from the import logs and exception text from `errors[]` — VERIFIED by reading; OF-T7 (the test that would keep it so) is missing (R2).
- Tenant scoping unchanged (`findByIdAndBranchId` before geocoding on update; import scoped to the principal's branch).

## Required before the next handoff

See `task-claude-code-rework.md`.

## Manual test scenarios (after the rework)

| # | Steps | Expected |
|---|---|---|
| OF-M1 (UO-M10) | CSV: one good row + one row with an unresolvable address; skip errors **off** | "Import refused: 1 rows have errors"; nothing imported; error line = the file line of the bad row |
| OF-M2 (UO-M11) | Same file, skip errors **on** | 1 imported, 1 error (once), no 500 |
| OF-M3 (UO-M5) | Edit an order, clear Notes and Phone, Save, reload | Notes and phone empty |
| OF-M4 (UO-M6) | Edit an order, change only the address | Hint shown; after Save the marker moves to the new address |
| OF-M5 | Edit an order, set only Start time | Dialog error "Enter both start and end time, or leave both empty."; nothing sent |
| OF-M6 | Import a `.txt` renamed file through the API (or any non-CSV/JSON name) | 400 message in correct Vietnamese: "Định dạng file không hỗ trợ (chỉ CSV/JSON)." |

Record every order code created; they are test rows in the shared database.
