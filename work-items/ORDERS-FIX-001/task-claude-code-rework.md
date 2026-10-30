# Rework task for Claude Code — ORDERS-FIX-001

Reason: `work-items/ORDERS-FIX-001/review.md` (2026-10-31 00:05 +07:00), findings R1–R5. Authorization: the original task (user 2026-10-30 23:27). Same rules as `task-claude-code.md` (AGENTS.md §10, §14, §15; allowed and forbidden files unchanged; no new `@SpringBootTest`; no commit).

**Do not shortcut the tests again.** If a designed test is truly impossible, stop and report the concrete blocker (command, error) before handing off; "complex setup" is not a blocker.

## Step 0 — ledger back to implement
```
git branch --show-current            # expect fix/orders-fix-001
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json code_failed work-items/ORDERS-FIX-001/05-proof-code-failed.json
```
Expect state `implement`, revision 5.

## Step 1 — R1 encoding
1. In `GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/orders/OrderImportService.java` restore exactly:
   - `"Định dạng file không hỗ trợ (chỉ CSV/JSON)."`
   - `"Lỗi hệ thống khi đọc file: {}"`
2. Save as UTF-8 **without BOM**. Edit with your file tools, not `Get-Content`/`Set-Content` in Windows PowerShell 5.1 (they default to the ANSI code page). If PowerShell must write a file, use `[System.IO.File]::WriteAllText(path, text, (New-Object System.Text.UTF8Encoding $false))`.
3. Scan every file changed on this branch for mojibake and record the output (expect none):
   ```
   git diff --name-only | ForEach-Object { Select-String -Path $_ -Pattern 'Ã|Ä‘|á»|â€' -Encoding utf8 }
   git ls-files --others --exclude-standard GVRP_Entry_API GVRP_Entry_client_v2 | ForEach-Object { Select-String -Path $_ -Pattern 'Ã|Ä‘|á»|â€' -Encoding utf8 }
   ```
4. `git diff -- GVRP_Entry_API/src/main/java/org/truong/gvrp_entry_api/service/orders/OrderImportService.java` must show no change to those two lines compared with `master`.

## Step 2 — R2 missing tests (Entry), per `design.md` §3
Follow `OrderImportDeliveryDateTest` (real `TextDataParser`, real validator, Mockito for repositories, mapper and geocoder). For CSV/JSON use the real `CsvFileParser` / `JsonFileParser` with `MockMultipartFile`.
- OF-T1, OF-T2 — ungeocodable row, skip on / off.
- OF-T4, OF-T5, OF-T6 — line numbers for CSV, text, JSON.
- OF-T7 — `ListAppender<ILoggingEvent>` on `OrderImportService` at `DEBUG` (Logback); assert no message or argument contains the customer name, phone, address, a coordinate or the thrown exception text; assert the DB error's message is `"Could not save this order."` and its line is the row's line.
- R5 — OF-T8: add `verify(orderRepository, never()).save(any())` and the message check on the update case.

## Step 3 — R2/R3 client
1. Baseline first, on the current code: `npx ng test --watch=false --browsers=ChromeHeadless`. Record the count and every failure by name. A failure in an existing spec caused by this branch's C1/C2 change → **stop and report** (do not edit the spec).
2. Add new spec files: OF-C1 (`clearStaleCoordinates`), OF-C2 (edit dialog: address-only change → `updateOrder` with null coordinates; hint visible), OF-C3 (one time bound → error, no API call).
3. R4: make `showStaleCoordinateHint` call `clearStaleCoordinates` (no duplicated rule).
4. Run Karma again and `rm -rf dist && npm run build`.

## Step 4 — verify
- `.\mvnw.cmd -B clean test`: all green; count = 317 + new.
- Karma all green; count = Step 3.1 count + new. Build EXIT 0, no new warning.
- `git status --short`: only allowed files.

## Step 5 — record and hand off
Write `work-items/ORDERS-FIX-001/implementation-rework.md` (do not edit `implementation.md`: its hash is evidence of revision 4): Findings Addressed (R1–R5, one line each with the evidence), Commands Executed (exit codes and counts), Encoding Scan Output, Client Baseline, Tests Added (names), Modified Tests ("none", or the stop report), Remaining Uncertainty, Lesson Candidate (PowerShell 5.1 encoding — proposal only). Then:
```
node tools/feature-workflow/workflow.mjs step work-items/ORDERS-FIX-001/run.json ready work-items/ORDERS-FIX-001/06-proof.json
```
Expect state `verify`, revision 6. Tell the user you are done, in Vietnamese.
