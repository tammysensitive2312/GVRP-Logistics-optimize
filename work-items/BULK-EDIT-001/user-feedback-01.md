# User feedback — BULK-EDIT-001 (failure report, 2026-10-11)

Source: user testing the rev-7 handoff.

- Symptom: clearing the date picker still sends today's date in the request.
- Root cause: `buildFilterObject` defaults empty date to today (`formValues.date || today`), and `OrderFilter.date` is required so `api.service` always sends it. Backend correctly treats a missing date as "no filter" — the frontend never lets it be missing.
- Fix: make `date` optional end-to-end (model, builder omits when cleared, api omits param). Initial default (today) on first load unchanged. No requirement/design change (BE-003 already says date is optional server-side).
