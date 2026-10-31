# Review — DOC-SYSTEM-001

Reviewer: Claude (cloud session), 2026-10-30. Read against `master` @ `081640a` (`docs/system-design-001` fast-forwarded into `master`).
Scope read: the `GVRP_Document` tree, `README.md` of D02 and D03, `04-test-design/test-cases.csv` (header and first rows), `_history/work-item-index.md`, `implementation.md`; code spot-checks listed under each finding.
Not re-run: DS-V1…V9 scripts, PlantUML rendering.

## Verdict

**Accepted with corrections.** Structure, levels, diagrams, history and deletions match the design; the 14 closed work items are indexed with commit and destination; the five open items are untouched. The hand-over between OpenCode and Claude Code is recorded honestly (reconstructed baseline, rev-9 proof copy). Three statements in the basic design contradict the code (F1, F3, F4), one real defect is missing from D02 (F2), and the Excel deliverable is not produced (F5).

## Findings

| ID | Severity | Status | Finding |
|---|---|---|---|
| F1 | MEDIUM | VERIFIED by reading | D03 §4 lists the import form fields in snake_case (`delivery_date`, `text_data`, `overwrite_existing`, `skip_validation_errors`). The endpoint binds `@ModelAttribute`, which uses Java property names; the client sends `deliveryDate`, `textData`, `serviceTime`, `overwriteExisting`, `skipValidationErrors`, and test UO-T7 asserts `deliveryDate`. A reader following the document gets every field ignored. |
| F2 | MEDIUM | VERIFIED by reading (not run) | Missing defect in D02: `GeometryMapper.validateCoordinates` ends with an unconditional `throw new DataInvalidException(errorsInfo)`, and `DepotMapper.toEntity/updateEntityFromDTO` use `createPoint(DepotInputDTO)`. So `POST /api/v1/depots` with valid coordinates answers `400` with an empty detail list, not `201` as D02 §2 says. This also blocks the setup wizard's first step for a new branch. Needs a code fix item and a D02 row. |
| F3 | LOW | VERIFIED by reading | D03 §2 says `PUT /orders/{id}` is "full replacement of the editable fields". `BaseMapperConfig` uses `NullValuePropertyMappingStrategy.IGNORE`, so `null` keeps the stored value (see UX-ORDERS-001 review F2). |
| F4 | LOW | PLAUSIBLE | D03 §4 step 6 says a row that fails at the database "is appended to `errors[]` and the loop continues". The loop runs inside one `@Transactional`; a persistence exception from `save` marks it rollback-only, so the import is expected to end in `500` with nothing saved (UX-ORDERS-001 review F1). Reword after that fix. |
| F5 | MEDIUM (deliverable) | VERIFIED | `GVRP_Test_Cases.xlsx` is not generated (`openpyxl` missing on the host; installing was out of scope). `test-cases.csv` and `tools/build_test_cases.py` exist. The Excel can be produced outside the repository host and committed. |
| F6 | INFO | VERIFIED | Broken references to deleted work items remain in `docs/security/SECURITY_CHECKLIST.md`, `docs/engineering/LESSONS.md`, `docs/research/observations/OBJ-001-…md`, `.github/workflows/ci.yml` (comment), `tools/session-brief.test.mjs` (fixture string) — listed in `implementation.md`; to fix at closure using `_history/work-item-index.md`. |
| F7 | INFO | from `implementation.md` | `02-system-design/05-data-architecture.md` contains `SELECT COUNT(*)` row counts that OpenCode read from `gvrp_db`; design §3 allowed schema queries only. Counts only, no row content; acceptable, but the counts go stale — consider removing them. |
| F8 | INFO | from `implementation.md` | 10 `ASSUMPTION:` markers remain; listed per file. Keep them until each is checked. |

## Checks for the user

1. Open `GVRP_Document/README.md` and follow the links: every diagram should open as SVG.
2. Open `03-basic-design/D07-lez/README.md` and compare with the zone editor you used today (drawing, presets, warnings must be described, since the documentation was written at `c52bb17`, after UX-LEZ-001).
3. Decide F5: generate the `.xlsx` (Claude can build it from `test-cases.csv` in the cloud workspace) or keep CSV only.
