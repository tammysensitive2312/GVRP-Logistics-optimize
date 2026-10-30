# Handoff — ORDERS-FIX-001

Version: branch `fix/orders-fix-001`, working tree verified on 2026-10-31 (`verification.md`); the scope fingerprint is the `handoff` value the workflow records on `pass`.

## What changed for the user

- Import: a row whose address cannot be geocoded is reported (with its file line) and never saved; every import error carries the line of the source file (CSV, JSON or pasted text); a database failure shows *Could not save this order.* without technical text; import logs no longer contain customer data.
- Edit order: clearing phone, notes or time window really clears them; changing only the address re-locates the order (a hint explains it); a single start or end time is refused in the dialog and by the API.

## How to test (manual, optional)

| # | Steps | Expected |
|---|---|---|
| OF-M1 | Import a CSV with one good row and one row with an unresolvable address; skip errors **off** | "Import refused: 1 rows have errors"; nothing imported; error line = file line of the bad row |
| OF-M2 | Same file, skip errors **on** | 1 imported, 1 error, no 500 |
| OF-M3 | Edit an order, clear Notes and Phone, Save, reload | Notes and phone empty |
| OF-M4 | Edit an order, change only the address | Hint shown; after Save the marker moves |
| OF-M5 | Edit an order, set only Start time | "Enter both start and end time, or leave both empty."; nothing sent |
| OF-M6 | Import a non-CSV/JSON file name through the API | 400 "Định dạng file không hỗ trợ (chỉ CSV/JSON)." |

Record every order code created.

## Known limitations

See `verification.md` § Limitations: no browser session, manual scenarios not run, one DB failure still rolls back the whole import.
