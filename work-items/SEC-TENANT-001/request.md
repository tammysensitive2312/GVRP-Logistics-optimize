# Request — SEC-TENANT-001 (cross-branch access fixes)

Source: user message 2026-10-16 ("open a work item to fix SEC-002 and check
other controllers, can you do it for me?"). Origin: `docs/security/SECURITY_CHECKLIST.md`
SEC-002 and SEC-003. Authorization: implementation authorized by that message.

Context: each `Branch` is a separate company (user statement 2026-10-16).
Cross-branch access is the highest-severity defect class (AGENTS.md section 15).

## Goal

No authenticated user can read or modify another branch's data through the
Entry API endpoints audited in `context.md`.

## Requirements

- TI-001: `/api/v1/solutions/{id}` read endpoints (`GET /{id}`,
  `GET /{id}/latest-revision`, `GET /{id}/revision-chain`) return not-found for
  a solution of another branch.
- TI-002: `POST /api/v1/solutions/{id}/evaluate` and `POST /{id}/revisions`
  return not-found for a base solution of another branch and persist nothing.
- TI-003: `POST /api/v1/vehicles?fleetId=` refuses a fleet, depot or vehicle
  type of another branch.
- TI-004: `PUT /api/v1/vehicles/{id}` refuses a vehicle of another branch and
  refuses depots or vehicle types of another branch.
- TI-005: `POST /api/v1/fleets` refuses depots or vehicle types of another branch.
- TI-006: `GET /api/v1/routes/{id}/sends` returns not-found for a route of
  another branch.
- TI-007: the branch always comes from the authenticated principal
  (`CurrentUserUtil.getCurrentBranchId()`); a missing branch fails closed.
- TI-008: each fix has a unit test proving the cross-branch refusal; all
  existing tests keep their assertions.

## Exclusions (recorded as separate findings, not fixed in this run)

- Role-based (vertical) authorization: a DRIVER can call planner endpoints of
  its own branch (SEC-006).
- Global license-plate uniqueness check reveals plate existence across
  branches (SEC-007, low).
- `/api/v1/auth/register` stub reports success without creating a user (SEC-008, low).
- Credential rotation (SEC-001) is a user action.

## Acceptance expectations

- `mvnw.cmd test` in `GVRP_Entry_API`: new cross-branch tests pass; no new
  failures versus the pre-change baseline.
- User manual check: log in as a planner of branch A, request a solution ID of
  branch B, receive not-found.
