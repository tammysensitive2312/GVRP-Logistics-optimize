# Verification — SEC-ROLE-001

Run by the user on 2026-10-16 23:48 (+07:00), independently of the engineer.

| Check | Result |
|---|---|
| `mvnw.cmd clean test` in `GVRP_Entry_API` | 80/80 pass (69 pre-existing + 11 `RoleAccessTest`) |
| Independent code review | `review.md`: ACCEPTED_WITH_LIMITATIONS, no deviation from design, compared existing tests unchanged |
| Manual: DRIVER `GET /api/v1/orders` | 403, body `{"errors":[{"code":"0040301","message":"Access forbidden."}]}` |
| Manual: DRIVER `GET /api/v1/driver/routes`, PLANNER web client | OK (user report "tất cả đều oke") |
| DB role check `SELECT role, COUNT(*) FROM users GROUP BY role` | DRIVER 1, PLANNER 7, CUSTOMER 0: RA-003 locks out nobody |

## Requirement coverage

| Req | Status |
|---|---|
| RA-001 | VERIFIED_BY_TEST + VERIFIED_AT_RUNTIME (manual, local) |
| RA-002 | VERIFIED_BY_TEST + VERIFIED_AT_RUNTIME (web client walkthrough) |
| RA-003 | VERIFIED_BY_TEST; no CUSTOMER users exist |
| RA-004 | VERIFIED_BY_TEST + VERIFIED_AT_RUNTIME |
| RA-005 | VERIFIED_BY_TEST for anonymous 401 and login; callback path by rule order only (static) |
| RA-006 | VERIFIED: slice test needs no DB; not in CI exclusion list |
| RA-007 | `RouteSendService` unchanged; `RouteSendServiceTest` passes |

## Not executed

- CI run on GitHub (branch not yet pushed).
- Automated test of the callback path under the new rules.
