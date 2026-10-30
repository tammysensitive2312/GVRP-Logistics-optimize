# Feature traceability

Feature ID: DEPOT-FIX-001
Inspected source revision: `master` @ `081640a`, 2026-10-30 23:30–23:45 +07:00 (read only, not run)
Request source: `work-items/DEPOT-FIX-001/request.md` (user messages 2026-10-30 23:25, 23:27)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DF-R1 | Valid depot create answers 201 (DOC-SYSTEM-001 review F2; user 23:27) | S1 | Planned: `GeometryMapper.validateCoordinates` | DF-T1, DF-T4; live check | None | NOT_VERIFIED |
| DF-R2 | Out-of-range coordinate still 400 with the field named | S1 | Planned: `GeometryMapper.validateCoordinates` | DF-T2, DF-T3 | None | NOT_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Separate fix item for the depot defect | Review summary pasted 23:25; user 23:27 | DF-R1, DF-R2 | — |
| No documentation change in this item | User 23:27 ("Phần document tạm thời remaining chưa động tới") | — | D02 row stays with the DOC-SYSTEM-001 corrections |

## Synchronization review

- Requirements versus implementation: to fill at verify.
- Design versus implementation: to fill at verify.
- Test and documentation coverage: `GVRP_Document` D02 does not list this defect (DOC-SYSTEM-001 review F2); intentionally not updated here.
- Stale links or changed assumptions: none known.
- Remaining limitations: live `201` NOT_VERIFIED until the live check is run.
