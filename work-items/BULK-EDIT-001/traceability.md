# Feature traceability

Feature ID: BULK-EDIT-001
Inspected source revision: working tree at design time
Request source: work-items/BULK-EDIT-001/request.md; decisions: user-decision-01.md (Q-1..Q-4)

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| BE-001 | Bulk set status, graph-legal targets (Q-1) | BULK_EDIT_ORDERS.md §1.2 | PATCH orders/status + lenient service | service + controller specs | verification.md | VERIFIED (component scope) |
| BE-002 | Per-row report (user-decision-01) | Same §1.2, §2 | updated/failed lists + report UI | component specs | verification.md | VERIFIED (component scope) |
| BE-003 | Server-side filters (Q-3) | Same §1.1 | GET orders params + JPQL | filter specs + live S-3 pending | verification.md | VERIFIED (component scope) |
| BE-010/011 | Guarded + lenient (Q-2) | Same §1.2 | bulkTransitionLenient reuse | partition specs | verification.md | VERIFIED (component scope) |
| BE-012/013/014 | Branch scope, empty guard, single-page (Q-4) | Same §1.2, §2 | checks + selection clear | specs | verification.md | VERIFIED (component scope) |
| BE-020/021 | Unknown ids, transport failure | Same §3 | failed rows + reload | specs | verification.md | VERIFIED (component scope) |
| BE-030..033 | Acceptance S-1..S-3 + suites | Same §4 | fixture manual + suites | suites green; manual pending user test | verification.md | PARTIALLY_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| Lenient via existing bulkTransitionLenient | user-decision-01 (Q-2) | BE-001, BE-011 | None |
| Single-page selection + clear on change | user-decision-01 (Q-4) | BE-014 | None |
| Filter fix in this run | user-decision-01 (Q-3) | BE-003 | None |
| BE-004/005 excluded | user-decision-01 | — | Future runs |

## Synchronization review

- Requirements versus implementation: all BE implemented per accepted decisions.
- Design versus implementation: matches (snake_case wire pinned in specs after the DRIVER-DELIVERY lesson).
- Test and documentation coverage: Entry 9/9, client 172 + 5 pre-existing, tsc 0, budgets unchanged.
- Stale links or changed assumptions: none.
- Remaining limitations: live manual S-1..S-3 needs redeployed stack + seeded branch.
- Repair 1 (`user-feedback-01.md`): optional-date fix + omit/keep specs; no requirement/design impact.
- Repair 2 (`user-feedback-02.md`): full-replace merge + status options + merge spec; verified live on the wire.
