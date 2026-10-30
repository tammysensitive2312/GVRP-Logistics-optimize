# Review — DEPOT-FIX-001

Reviewer: Claude (cloud session), 2026-10-30 23:45 +07:00. Read through the device bridge: the working tree of the repository (HEAD = `master` @ `081640a`), `implementation.md`, `run.json` (state `verify`, rev 4), `GeometryMapper.java`, `GeometryMapperTest.java`, `DepotServiceCreateTest.java`, `AppConstant.java`, `.git/HEAD`, `.git/refs/heads/`, `.git/logs/HEAD`.
Not re-run: Maven (the engineer's 314 → 321, 0 failures, is taken as recorded). `git status` could not be run (no shell on the host from this session).

## Verdict

**Code accepted; one process defect to fix before commit (R1).** S1 is exactly the designed one-line guard. The tests are DB-free (L-2026-001), assert the right things, and DF-T4 exercises the real generated `DepotMapperImpl`, which is stronger than the design required. No existing test changed. Tenant scoping unchanged.

## Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| R1 | MEDIUM (process) | VERIFIED | The fix branch was **never created**. `.git/HEAD` = `refs/heads/master`; `.git/refs/heads/` has no `fix/` entry and there is no `packed-refs`; the last `logs/HEAD` entry is the fast-forward of `docs/system-design-001` (no `checkout: … to fix/depot-fix-001`). So the change sits uncommitted on `master`, while `implementation.md` §Baseline states "Fix branch: `fix/depot-fix-001`" — an untrue statement in the evidence (AGENTS.md §10). Remedy (user): `git switch -c fix/depot-fix-001` (carries the uncommitted changes), then commit. Correct the Baseline line of `implementation.md` at closure. |
| R2 | LOW (design error, accepted deviation) | VERIFIED | The design expected detail fields `latitude`/`longitude`; `AppConstant.LATITUDE`/`LONGITUDE` are `"lat"`/`"long"`. The engineer asserted the real values and reported the deviation instead of changing the constant — correct. The two refusal paths name the field differently (Bean Validation: `latitude`; mapper: `lat`), but the mapper path is unreachable through the endpoint because `@DecimalMin/@DecimalMax` refuse first. No action. |
| R3 | INFO | VERIFIED by reading | The live check was skipped because "no Entry API was reachable on port **18080**". `CLAUDE.md` gives the Entry API port as **8080**. The skip is honestly recorded, but the reason may rest on the wrong port. The live check (below) is still open. |
| R4 | INFO | VERIFIED | `traceability.md` still shows both rows `NOT_VERIFIED` / evidence "None". Update at closure: DF-R1 PARTIALLY_VERIFIED (unit, no HTTP), DF-R2 VERIFIED (unit), with the test names. |

## Security

- No new endpoint, no `permitAll`, branch from `CurrentUserUtil.getCurrentBranchId()` — VERIFIED by reading `DepotController`/`DepotService` (unchanged).
- No secrets or personal data in the new tests (fictional depot name and a public street address).

## Manual test (browser or API)

| # | Steps | Expected |
|---|---|---|
| DF-M1 | Entry API on 8080; log in to a branch; create a depot (depot page or setup wizard step 1) with a valid address and a map point | Created, listed; response `201` in DevTools |
| DF-M2 | `POST /api/v1/depots` with latitude `95` | `400`, detail field `latitude` (Bean Validation path) |
| DF-M3 | Log in to another branch, list depots | The depot from DF-M1 is not listed |

Record the depot name created; it is a test row in the shared database.

## Closure checklist

1. User: `git switch -c fix/depot-fix-001`, commit (code + `work-items/DEPOT-FIX-001/`).
2. User: DF-M1…M3.
3. On acceptance: fix the Baseline line (R1) and `traceability.md` (R4), record acceptance, move the run on.
4. Lesson candidate L-2026-015 (unconditional `throw` in a validation helper): only with the user's approval.
5. D02 row stays for the documentation pass (user 23:27).
