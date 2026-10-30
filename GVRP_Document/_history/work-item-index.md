# Deleted work items

Work items whose content was migrated into this documentation set and whose folders were then
removed from `work-items/`. Git keeps the full history; every row below is reproducible with
`git log -- work-items/<ID>` at the commit named in the row.

All work items listed had `state: done` in their `run.json` (`node tools/feature-workflow/workflow.mjs
status work-items/<ID>/run.json`) before deletion, except PARETO-004, which never had a run (its
folder holds a single `change-note.md`; the deletion was explicitly authorised in `request.md`).

| ID | Title | State | Last commit | Migrated to |
|---|---|---|---|---|
| BULK-EDIT-001 | Bulk status edit for orders (BE-001…BE-033) and server-side order filters | `done` rev 18 | `7a0c4ce` 2026-10-16 | `_history/decisions/BULK-EDIT-001.md`; API row `PATCH /api/v1/orders/status` and `BR-031`…`BR-034`; `03-basic-design/D03-orders/README.md`; `TC-D03-014`…`TC-D03-016`, `TC-D03-020`…`TC-D03-022` |
| CI-CLIENT-001 | Client v2 build and test job in CI | `done` rev 8 | `8851067` 2026-10-20 | `_history/decisions/CI-CLIENT-001.md`; `04-test-design/01-test-strategy.md` (CI level) |
| DRIVER-DELIVERY-001 | Driver delivery web view and the route-send backend (Chapter 1) | `done` rev 18 | `de58232` 2026-10-24 | `_history/decisions/DRIVER-DELIVERY-001.md`; whole of `03-basic-design/D06-dispatch/`; `TC-D06-001`…`TC-D06-015` |
| DRIVER-MAP-001 | Driver map, per-leg Google Maps links, `sent_at` formatting | `done` rev 13 | `de58232` 2026-10-24 | `_history/decisions/DRIVER-MAP-001.md`; `03-basic-design/D06-dispatch/README.md` (screens) |
| DRIVER-MAP-002 | Driver map v2: in-app guidance, GPS dot, responsive layout | `done` rev 13 | `7a0c4ce` 2026-10-16 | `_history/decisions/DRIVER-MAP-002.md`; `03-basic-design/D06-dispatch/README.md` |
| DRIVER-MAP-003 | Follow mode with an in-app permission gate | `done` rev 10 | `7a0c4ce` 2026-10-16 | `_history/decisions/DRIVER-MAP-003.md`; `TC-D06-018` |
| DRIVER-MAP-004 | Progressive disclosure of driver route sections | `done` rev 11 | `de58232` 2026-10-24 | `_history/decisions/DRIVER-MAP-004.md`; `TC-D06-019` |
| PARETO-001 | Engine Pareto branch: clustering, frontier transport, persistence, apply | `done` rev 7 | `4e2f928` 2026-10-18 | `_history/decisions/PARETO-001.md`; `03-basic-design/D05-solution/diagrams/sequence-pareto.puml`; `BR-041`…`BR-046`; `TC-D04-016`, `TC-D05-012`…`TC-D05-015`, `TC-D08-003`, `TC-D08-004` |
| PARETO-002 | Client v2 Pareto tab: chart and plan switching | `done` rev 10 | `21d2805` 2026-10-19 | `_history/decisions/PARETO-002.md`; `03-basic-design/D05-solution/README.md` (screens) |
| PARETO-003 | Zoom and pan in the Pareto trade-off chart | `done` rev 7 | `21d2805` 2026-10-19 | `_history/decisions/PARETO-003.md`; `03-basic-design/D05-solution/README.md` |
| PARETO-004 | Delivered-demand service tier; deprecate `SolutionCandidate.dominates()` | no run (`change-note.md` only) | `6dd718c` 2026-10-24 | `_history/decisions/PARETO-004.md`; `BR-045`; `03-basic-design/D08-engine/README.md` |
| SEC-ROLE-001 | Role-based access to the Entry API | `done` rev 7 | `d67daa7` 2026-10-16 | `_history/decisions/SEC-ROLE-001.md`; `02-system-design/04-security-and-tenancy.md`; `01-requirements/01-actors-and-roles.md` |
| SEC-SESSION-001 | Disabled users must lose API access | `done` rev 7 | `e98ec95` 2026-10-17 | `_history/decisions/SEC-SESSION-001.md`; `02-system-design/04-security-and-tenancy.md`; `TC-D01-010`, `TC-D01-011` |
| SEC-TENANT-001 | Cross-branch access fixes (TI-001…TI-008) | `done` rev 7 | `6160126` 2026-10-16 | `_history/decisions/SEC-TENANT-001.md`; `02-system-design/04-security-and-tenancy.md`; `BR-001`…`BR-005`; the tenant-isolation cases of `04-test-design/test-cases.csv` (e.g. `TC-D02-010`, `TC-D03-017`, `TC-D05-002`, `TC-D06-009`, `TC-D07-021`) |

## Content that was not carried over

| Item | Reason |
|---|---|
| `work-items/BULK-EDIT-001/**` proof and feedback files | process evidence for a finished run; reproducible from git |
| `work-items/CI-CLIENT-001/implementation.md` (17 KB) and `run.json` (73 KB) | command log of a finished run; the lesson that survived is in `_history/decisions/CI-CLIENT-001.md` |
| `work-items/PARETO-001/design.md` (23 KB) | superseded by `03-basic-design/D05-solution/diagrams/sequence-pareto.puml` and `BR-041`…`BR-046`; the research part (comparing clustered and dense frontiers) was never executed and is recorded as open in `docs/research/PROJECT_STATE.md` |
| `work-items/*/verification*.md`, `handoff*.md`, `review.md` | per-run evidence; the accepted behaviour is now stated in the basic design and the test design |

`BULK-EDIT-001/closure.md` cites `GVRP_Document/BULK_EDIT_ORDERS.md` as its design document. That
file is not present at `c52bb17`; it exists only in the history of the work item.

## Out of scope

`work-items/LEZ-001`, `MAP-ORDERS-001`, `BROWSER-FIX-001`, `UX-ORDERS-001`, `UX-LEZ-001` were read
as sources and left untouched.
