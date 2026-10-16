# Feature traceability

Feature ID: DRIVER-DELIVERY-001 (chặng 1; Flutter chặng 2 reuses driver APIs)
Request source: work-items/DRIVER-DELIVERY-001/request.md; decisions: user-decision-01.md (Q-1/2/3 + summary acceptance)

Design artifacts (all inside `work-items/DRIVER-DELIVERY-001/` per user instruction):
`scenarios.md` (S-1..S-4), `../../GVRP_Document/diagrams`,
`../../GVRP_Document/diagrams`, `../../GVRP_Document/diagrams`,
plus `GVRP_Document/DRIVER_DELIVERY.md` (kept as the prose contract).

| Requirement ID | Accepted behavior and source | Design section | Code file / symbol | Test / manual case | Evidence and version | Status |
|---|---|---|---|---|---|---|
| DD-001 | Driver directory linked to accounts (user-decision-01) | DRIVER_DELIVERY.md §1 | User.phone + findByBranchIdAndRole; DriverDTO | dialog + service directory specs | verification.md | VERIFIED (component scope) |
| DD-002 | DRIVER role + shared login (Q-1) | Same §1, §2 | UserRole, JWT unchanged; getRole/guard | guard specs; login untouched | verification.md | VERIFIED (component scope) |
| DD-003 | Per-send driver assignment (user-decision-01) | Same §2 | POST routes/{id}/send | assignment + dialog specs | verification.md | VERIFIED (component scope) |
| DD-004 | Driver-scoped read APIs (user-decision-01) | Same §2 | GET driver/routes[/{id}] + ownership checks | 403 matrix + guard specs | verification.md | VERIFIED (component scope) |
| DD-005 | Driver view content (user-decision-01) | Same §2, §3 | order-join detail + view | component specs (phone/notes) | verification.md | VERIFIED (component scope) |
| DD-006 | Latest + revision select (user-decision-01) | Same §2, §3 | revision-chain endpoint + dialog | dialog specs | verification.md | VERIFIED (component scope) |
| DD-007 | Supersede + banner, no push (Q-2) | Same §2, §3 | status flip + banner | supersede + banner specs | verification.md | VERIFIED (component scope) |
| DD-020..023 | Send records, supersede, isolation, sendable status | Same §2, §4 | RouteSend + checks | rule specs | verification.md | VERIFIED (component scope) |
| DD-030..032 | Disabled-driver/empty/bad-revision handling | Same §4 | guards | exception specs | verification.md | VERIFIED (component scope) |
| DD-040..043 | Acceptance walkthroughs | Same §5 | fixture manual + suites | suites green; manual pending user test | verification.md | PARTIALLY_VERIFIED |

## Decision log

| Decision | User source or existing authority | Affected requirements | Follow-up |
|---|---|---|---|
| No Driver entity; directory = DRIVER users (+phone) | Agent simplification under accepted DD-001 | DD-001 | None unless user objects |
| Shared auth, role in JWT already present | Existing JwtAuthenticationFilter + login response | DD-002, Q-1 | None |
| Service-level role/ownership checks (no precedent) | New requirement DD-022/DD-041 | DD-004 | Review carefully at verify |
| Flutter deferred; driver APIs contract-stable | User 2-phase decision | All | Chặng 2 run |

## Synchronization review

- Requirements versus implementation: all DD implemented per accepted decisions; NG0600 assignee-loading defect found by tests and fixed (queueMicrotask decoupling).
- Design versus implementation: matches, plus one compatible addition (`GET routes/{id}/sends` for assignee display).
- Test and documentation coverage: Entry 14/14, client 140 + 5 pre-existing, tsc 0, budgets match baseline.
- Stale links or changed assumptions: none.
- Remaining limitations: live run needs user redeploy (Entry migration + code); 409-concurrency and cross-driver probing covered by unit tests only.
- Repair 1 (`user-feedback-01.md`): snake_case wire fix + contract-pinning spec; no requirement/design impact.
- Repair 2 (`user-feedback-02.md`): DRIVER post-login landing + 3 specs; backend login verified role-agnostic by inspection.
